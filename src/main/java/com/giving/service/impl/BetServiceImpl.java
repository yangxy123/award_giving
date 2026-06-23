package com.giving.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.MethodEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.ProjectsTmpEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.entity.UserEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.LotteryMapper;
import com.giving.mapper.MethodMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.ProjectsTmpMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.mapper.TempIssueInfoMapper;
import com.giving.mapper.UserFundMapper;
import com.giving.mapper.UserMapper;
import com.giving.req.BetOrderReq;
import com.giving.req.LtProjectReq;
import com.giving.resp.BetOrderResp;
import com.giving.service.BetService;
import com.giving.service.UserFundLockTxService;
import com.giving.service.context.BetContext;
import com.giving.util.TableNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BetServiceImpl implements BetService {
    private static final int ORDER_TYPE_JRYX = 3;
    private static final int WALLET_TYPE_BET = 1;
    private static final int MAX_PROJECT_COUNT = 800;
    private static final String JOIN_GAME = "加入游戏";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private LotteryMapper lotteryMapper;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;
    @Autowired
    private MethodMapper methodMapper;
    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private UserFundLockTxService userFundLockTxService;

    /**
     * 投注
     * @param req 投注请求
     * @return 投注结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResp<BetOrderResp> order(BetOrderReq req) {
        boolean locked = false;
        String title = null;
        String userId = req.getUserId();
        try {
            if (Boolean.TRUE.equals(req.getOrderFuture())) {
                return ApiResp.bussError("追号投注暂未实现");
            }

            BetContext context = buildContext(req);
            title = context.getTitle();
            validateBasicOrder(req, context);

            locked = userFundLockTxService.doLockUserFund(userId, true, WALLET_TYPE_BET, "BET_001", title);
            if (!locked) {
                return ApiResp.bussError("用户资金上锁失败");
            }

            UserFundEntity userFundSum = userFundMapper.selectByUserSum(title, userId);
            UserFundEntity betWallet = userFundMapper.selectByUserAndType(title, userId, WALLET_TYPE_BET);
            if (userFundSum == null || betWallet == null) {
                throw new BetBusinessException("未查询到用户钱包");
            }

            BigDecimal totalAmount = req.getLtMoneyAmout();
            if (nvl(betWallet.getAvailablebalance()).compareTo(totalAmount) < 0) {
                throw new BetBusinessException("余额不足");
            }

            List<BetInfoEntity> projects = buildProjects(req, context);
            List<ProjectsTmpEntity> projectsTmp = buildProjectsTmp(context, projects);
            List<OrdersEntity> orders = buildOrders(context, projects, userFundSum);

            if (betInfoMapper.insertProjects(title, projects) != projects.size()) {
                throw new IllegalStateException("写入注单失败");
            }
            if (projectsTmpMapper.insertProjectsTmp(title, projectsTmp) != projectsTmp.size()) {
                throw new IllegalStateException("写入注单临时表失败");
            }
            if (ordersMapper.addOrdersListAll(orders, title) != orders.size()) {
                throw new IllegalStateException("写入账变失败");
            }
            if (userFundMapper.freezeBetAmount(title, userId, WALLET_TYPE_BET, totalAmount) <= 0) {
                throw new BetBusinessException("余额不足");
            }

            context.setProjectList(projects);
            return ApiResp.sucess(buildResponse(req, context, userFundSum, totalAmount));
        } catch (BetBusinessException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResp.bussError(e.getMessage());
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("普通投注失败，用户ID={}，彩种ID={}", userId, req.getLotteryId(), e);
            return ApiResp.bussError("投注失败");
        } finally {
            if (locked) {
                userFundLockTxService.doLockUserFund(userId, false, WALLET_TYPE_BET, "BET_001", title);
            }
        }
    }

    /**
     * 建立投注上下文
     * @param req 投注请求
     * @return 投注上下文
     */
    private BetContext buildContext(BetOrderReq req) {
        BetContext context = new BetContext();

        RoomMasterEntity roomMaster = roomMasterMapper.selectOne(new LambdaQueryWrapper<RoomMasterEntity>()
                .eq(RoomMasterEntity::getMasterId, Integer.valueOf(req.getRoomMasterId())));
        if (roomMaster == null || !Integer.valueOf(1).equals(roomMaster.getIsActive())) {
            throw new BetBusinessException("厅主不存在或未启用");
        }
        String title = TableNameUtil.safePrefix(roomMaster.getTitle());
        if (!isLotteryInService(roomMaster.getLotteryInService(), req.getLotteryId())) {
            throw new BetBusinessException("彩种未开启");
        }

        UserEntity user = userMapper.selectByUserId(title, req.getUserId());
        validateUser(user);

        LotteryEntity lottery = lotteryMapper.selectById(Long.valueOf(req.getLotteryId()));
        if (lottery == null || Integer.valueOf(0).equals(lottery.getIsActive())) {
            throw new BetBusinessException("彩种不存在或未启用");
        }

        TempIssueInfoEntity issue = findIssue(title, req);
        validateIssue(issue);

        Map<Integer, MethodEntity> methodMap = loadMethodMap(req);

        context.setRoomMaster(roomMaster);
        context.setTitle(title);
        context.setUser(user);
        context.setLottery(lottery);
        context.setIssue(issue);
        context.setMethodMap(methodMap);
        return context;
    }

    /**
     * 校验用户投注资格
     * @param user 用户信息
     */
    private void validateUser(UserEntity user) {
        if (user == null) {
            throw new BetBusinessException("用户不存在");
        }
        if ("1".equals(user.getIsDeleted())) {
            throw new BetBusinessException("用户已删除");
        }
        if ("1".equals(user.getIsFrozen())) {
            throw new BetBusinessException("用户已冻结");
        }
        if ("0".equals(user.getLvtopId())) {
            throw new BetBusinessException("总代不能投注");
        }
    }

    /**
     * 查询投注奖期
     * @param title 厅主动态表前缀
     * @param req 投注请求
     * @return 奖期信息
     */
    private TempIssueInfoEntity findIssue(String title, BetOrderReq req) {
        Long lotteryId = Long.valueOf(req.getLotteryId());
        if ("now".equalsIgnoreCase(req.getLtIssueStart())) {
            return tempIssueInfoMapper.selectCurrentByTitle(title, lotteryId);
        }
        return tempIssueInfoMapper.selectByTitle(title, lotteryId, req.getLtIssueStart());
    }

    /**
     * 校验奖期销售时间
     * @param issue 奖期信息
     */
    private void validateIssue(TempIssueInfoEntity issue) {
        if (issue == null) {
            throw new BetBusinessException("奖期不存在");
        }
        Date now = new Date();
        if (issue.getSaleStart() == null || issue.getSaleEnd() == null
                || !issue.getSaleStart().before(now) || !issue.getSaleEnd().after(now)) {
            throw new BetBusinessException("当前奖期不在销售时间内");
        }
    }

    /**
     * 查询玩法并按ID映射
     * @param req 投注请求
     * @return 玩法映射
     */
    private Map<Integer, MethodEntity> loadMethodMap(BetOrderReq req) {
        Map<Integer, MethodEntity> methodMap = new LinkedHashMap<>();
        for (LtProjectReq projectReq : req.getLtProject()) {
            if (methodMap.containsKey(projectReq.getMethodId())) {
                continue;
            }
            MethodEntity method = methodMapper.selectById(projectReq.getMethodId());
            if (method == null) {
                throw new BetBusinessException("玩法不存在");
            }
            if (Integer.valueOf(1).equals(method.getIsClose())) {
                throw new BetBusinessException("玩法已关闭");
            }
            if (!req.getLotteryId().equals(method.getLotteryId())) {
                throw new BetBusinessException("玩法和彩种不匹配");
            }
            methodMap.put(method.getMethodId(), method);
        }
        return methodMap;
    }

    /**
     * 校验投注项数量与金额
     * @param req 投注请求
     * @param context 投注上下文
     */
    private void validateBasicOrder(BetOrderReq req, BetContext context) {
        if (req.getLtProject().size() >= MAX_PROJECT_COUNT) {
            throw new BetBusinessException("单次投注不能达到或超过800单");
        }

        BigDecimal totalMoney = ZERO;
        int totalNums = 0;
        for (LtProjectReq projectReq : req.getLtProject()) {
            MethodEntity method = context.getMethodMap().get(projectReq.getMethodId());
            validateMode(method, projectReq.getMode());
            BigDecimal modeRate = modesRate(projectReq.getMode());
            BigDecimal expectedMoney = projectReq.getOnePrice()
                    .multiply(BigDecimal.valueOf(projectReq.getNums()))
                    .multiply(modeRate)
                    .multiply(BigDecimal.valueOf(projectReq.getTimes()));
            if (expectedMoney.compareTo(projectReq.getMoney()) != 0) {
                throw new BetBusinessException("投注金额与单价、注数、模式或倍数不一致");
            }
            totalMoney = totalMoney.add(projectReq.getMoney());
            totalNums += projectReq.getNums();
        }
        if (totalMoney.compareTo(req.getLtMoneyAmout()) != 0) {
            throw new BetBusinessException("投注总金额不一致");
        }
        if (req.getLtProjectNum() != null && req.getLtProjectNum() != totalNums) {
            throw new BetBusinessException("投注总注数不一致");
        }
    }

    /**
     * 校验玩法是否支持该模式
     * @param method 玩法
     * @param mode 投注模式
     */
    private void validateMode(MethodEntity method, Integer mode) {
        if (mode == null) {
            throw new BetBusinessException("投注模式不能为空");
        }
        if (!StringUtils.hasText(method.getModes())) {
            return;
        }
        String needle = String.valueOf(mode);
        for (String item : method.getModes().split(",")) {
            if (needle.equals(item.trim())) {
                return;
            }
        }
        throw new BetBusinessException("玩法不支持该投注模式");
    }

    /**
     * 组装正式注单
     * @param req 投注请求
     * @param context 投注上下文
     * @return 注单列表
     */
    private List<BetInfoEntity> buildProjects(BetOrderReq req, BetContext context) {
        Date now = new Date();
        List<BetInfoEntity> projects = new ArrayList<>();
        for (LtProjectReq projectReq : req.getLtProject()) {
            BigDecimal modeRate = modesRate(projectReq.getMode());
            BigDecimal singlePrice = projectReq.getOnePrice()
                    .multiply(BigDecimal.valueOf(projectReq.getNums()))
                    .multiply(modeRate);
            BigDecimal point = projectReq.getKeepPoint() == null ? ZERO : projectReq.getKeepPoint();
            String hprize = StringUtils.hasText(projectReq.getHprize()) ? projectReq.getHprize() : "0";

            BetInfoEntity project = new BetInfoEntity();
            project.setProjectId(newBizId(context.getRoomMaster().getMasterId()));
            project.setUserId(req.getUserId());
            project.setPackageId("1");
            project.setTaskId("");
            project.setLotteryId(req.getLotteryId());
            project.setMethodId(projectReq.getMethodId());
            project.setIssue(context.getIssue().getIssue());
            project.setBonus(0D);
            project.setWinbonus(hprize);
            project.setCode(projectReq.getCodes());
            project.setCodeType(StringUtils.hasText(projectReq.getType()) ? projectReq.getType() : "digital");
            project.setSinglePrice(singlePrice.doubleValue());
            project.setMultiple(String.valueOf(projectReq.getTimes()));
            project.setTotalPrice(projectReq.getMoney().doubleValue());
            project.setWriteTime(now);
            project.setScode(buildScode(projectReq, hprize));
            project.setUpdateTime(now);
            project.setDeductTime(now);
            project.setBonusTime(now);
            project.setCancelTime(null);
            project.setIsDeduct(0);
            project.setIsCancel(0);
            project.setIsGetprize(0);
            project.setPrizeStatus(0);
            project.setGameCancelCount(0);
            project.setUserIp("0.0.0.0");
            project.setModes(String.valueOf(projectReq.getMode()));
            project.setHashvar("");
            project.setUserPoint(formatMoney(point.multiply(projectReq.getMoney())));
            project.setIsNew("1");
            project.setComefrom("");
            project.setPointStatus(0);
            project.setThirdPartyTrxId(null);
            project.setPlatform(StringUtils.hasText(req.getPlatform()) ? req.getPlatform() : "web");
            project.setLog(null);
            project.setWriteMicrotime(String.valueOf(System.currentTimeMillis() / 1000D));
            project.setCreatedAt(now);
            project.setUpdatedAt(now);
            project.setPointinfo(buildPointInfo(point, projectReq.getMoney(), hprize));
            projects.add(project);
        }
        return projects;
    }

    /**
     * 组装注单临时表记录
     * @param context 投注上下文
     * @param projects 正式注单
     * @return 临时注单
     */
    private List<ProjectsTmpEntity> buildProjectsTmp(BetContext context, List<BetInfoEntity> projects) {
        Date now = new Date();
        List<ProjectsTmpEntity> result = new ArrayList<>();
        for (BetInfoEntity project : projects) {
            ProjectsTmpEntity tmp = new ProjectsTmpEntity();
            tmp.setTmpId(newBizId(context.getRoomMaster().getMasterId()));
            tmp.setIssueId(context.getIssue().getIssueId());
            tmp.setLotteryId(project.getLotteryId());
            tmp.setProjectId(project.getProjectId());
            tmp.setStatus(0);
            tmp.setTmpValue(JSON.toJSONString(project));
            tmp.setCreatedAt(now);
            tmp.setUpdatedAt(now);
            result.add(tmp);
        }
        return result;
    }

    /**
     * 组装加入游戏账变
     * @param context 投注上下文
     * @param projects 正式注单
     * @param userFundSum 用户钱包汇总
     * @return 账变列表
     */
    private List<OrdersEntity> buildOrders(BetContext context, List<BetInfoEntity> projects, UserFundEntity userFundSum) {
        Date now = new Date();
        BigDecimal preBalance = nvl(userFundSum.getChannelbalance());
        BigDecimal runningAvailable = nvl(userFundSum.getAvailablebalance());
        BigDecimal runningHold = nvl(userFundSum.getHoldbalance());
        List<OrdersEntity> orders = new ArrayList<>();
        for (BetInfoEntity project : projects) {
            BigDecimal amount = BigDecimal.valueOf(project.getTotalPrice());
            OrdersEntity order = new OrdersEntity();
            order.setEntry(newBizId(context.getRoomMaster().getMasterId()));
            order.setLotteryId(project.getLotteryId());
            order.setMethodId(project.getMethodId());
            order.setTaskId(project.getTaskId());
            order.setProjectId(project.getProjectId());
            order.setFromuserId(project.getUserId());
            order.setOrderTypeId(ORDER_TYPE_JRYX);
            order.setIssue(project.getIssue());
            order.setTitle(JOIN_GAME);
            order.setAmount(amount);
            order.setDescription(JOIN_GAME);
            order.setPreBalance(preBalance);
            order.setPreAvailable(runningAvailable);
            order.setPreHold(runningHold);

            runningAvailable = runningAvailable.subtract(amount);
            runningHold = runningHold.add(amount);

            order.setChannelBalance(preBalance);
            order.setAvailableBalance(runningAvailable);
            order.setHoldBalance(runningHold);
            order.setClientIp("0.0.0.0");
            order.setProxyIp("0.0.0.0");
            order.setActionTime(now);
            order.setTimes(now);
            order.setUniqueKey(project.getProjectId() + "_" + ORDER_TYPE_JRYX);
            order.setModes(project.getModes());
            order.setPlatform(project.getPlatform());
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            orders.add(order);
        }
        return orders;
    }

    /**
     * 组装投注响应
     * @param req 投注请求
     * @param context 投注上下文
     * @param userFundSum 投注前用户钱包汇总
     * @param totalAmount 投注总金额
     * @return 投注响应
     */
    private BetOrderResp buildResponse(BetOrderReq req, BetContext context, UserFundEntity userFundSum, BigDecimal totalAmount) {
        BetOrderResp resp = new BetOrderResp();
        resp.setLotteryId(req.getLotteryId());
        resp.setAvailableBalance(nvl(userFundSum.getAvailablebalance()).subtract(totalAmount));
        resp.setCurrentIssue(context.getIssue().getIssue());
        resp.setProjectId(context.getProjectList().stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList()));
        resp.setTaskId(Collections.emptyList());
        resp.setMoney(totalAmount);
        resp.setErrmsg("");
        resp.setTaskStopBet(0);
        resp.setTaskStopBetMoney(ZERO);
        return resp;
    }

    /**
     * 组装scode JSON
     * @param projectReq 投注项
     * @param hprize 前端奖金
     * @return scode JSON
     */
    private String buildScode(LtProjectReq projectReq, String hprize) {
        Map<String, Object> scode = new LinkedHashMap<>();
        scode.put("scode", projectReq.getCodes());
        scode.put("scode_key", projectReq.getScodeKey() == null ? "" : projectReq.getScodeKey());
        scode.put("digitstr", projectReq.getDigitstr() == null ? "" : projectReq.getDigitstr());
        scode.put("oldcode", projectReq.getCodes());
        scode.put("code_name", projectReq.getUpperName() == null ? "" : projectReq.getUpperName());
        scode.put("method_id", projectReq.getMethodId());
        scode.put("selectType", projectReq.getSelectType() == null ? "" : projectReq.getSelectType());
        scode.put("onePrice", projectReq.getOnePrice().toPlainString());
        scode.put("total_bonus", ZERO);
        scode.put("hprize", hprize);
        scode.put("nums", projectReq.getNums());
        scode.put("codeType", projectReq.getCodeType() == null ? "" : projectReq.getCodeType());
        return JSON.toJSONString(scode);
    }

    /**
     * 组装返点信息 JSON
     * @param point 返点
     * @param money 投注金额
     * @param hprize 前端奖金
     * @return pointinfo JSON
     */
    private String buildPointInfo(BigDecimal point, BigDecimal money, String hprize) {
        Map<String, Object> pointInfo = new LinkedHashMap<>();
        pointInfo.put("level", "1");
        pointInfo.put("single_prize", hprize);
        pointInfo.put("point", point);
        pointInfo.put("point_price", formatMoney(point.multiply(money)));
        pointInfo.put("prize", Collections.singletonList(hprize));
        return JSON.toJSONString(pointInfo);
    }

    /**
     * 获取投注模式倍率
     * @param mode 投注模式
     * @return 模式倍率
     */
    private BigDecimal modesRate(Integer mode) {
        switch (mode) {
            case 1:
                return BigDecimal.ONE;
            case 2:
                return new BigDecimal("0.1");
            case 3:
                return new BigDecimal("0.01");
            case 4:
                return new BigDecimal("0.001");
            default:
                throw new BetBusinessException("投注模式错误");
        }
    }

    /**
     * 判断彩种是否在厅主服务清单中
     * @param lotteryInService 服务清单
     * @param lotteryId 彩种ID
     * @return 是否开启
     */
    private boolean isLotteryInService(String lotteryInService, Integer lotteryId) {
        if (!StringUtils.hasText(lotteryInService)) {
            return true;
        }
        String needle = String.valueOf(lotteryId);
        for (String item : lotteryInService.split(",")) {
            if (needle.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成16位业务ID
     * @param roomMasterId 厅主ID
     * @return 业务ID
     */
    private String newBizId(Integer roomMasterId) {
        int master = roomMasterId == null ? 0 : Math.abs(roomMasterId % 1000);
        String prefix = String.format("%03d", master);
        String millis = Long.toHexString(System.currentTimeMillis());
        if (millis.length() > 11) {
            millis = millis.substring(millis.length() - 11);
        }
        String random = Integer.toHexString(ThreadLocalRandom.current().nextInt(16, 256));
        return (prefix + millis + random).substring(0, 16);
    }

    /**
     * BigDecimal空值归零
     * @param value 原始值
     * @return 非空金额
     */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    /**
     * 格式化金额
     * @param value 金额
     * @return 字符串金额
     */
    private String formatMoney(BigDecimal value) {
        return value.setScale(6, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
    }

    private static class BetBusinessException extends RuntimeException {
        BetBusinessException(String message) {
            super(message);
        }
    }
}
