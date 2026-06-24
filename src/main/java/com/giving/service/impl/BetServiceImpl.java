package com.giving.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.auth.ClientAuthException;
import com.giving.auth.ClientUserSession;
import com.giving.auth.ClientUserSessionHolder;
import com.giving.base.resp.ApiResp;
import com.giving.entity.*;
import com.giving.mapper.*;
import com.giving.req.BetOrderReq;
import com.giving.req.LtProjectReq;
import com.giving.resp.BetOrderResp;
import com.giving.service.BetBonusLimitService;
import com.giving.service.BetOrderTxService;
import com.giving.service.BetService;
import com.giving.service.UserFundLockTxService;
import com.giving.service.context.BetBonusLimitCheckResult;
import com.giving.service.context.BetContext;
import com.giving.util.TableNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
    private static final int BUSINESS_TYPE_B2B_MW_TEST = 25;
    private static final int BUSINESS_TYPE_SINGLE_WALLET_TRIAL = 27;
    private static final List<Integer> ONE_MIN_3D_LOTTERY_IDS = Arrays.asList(52, 108, 109, 110);
    private static final List<Integer> ONE_MIN_3D_QZX3_METHOD_IDS = Arrays.asList(2899, 4308, 4334, 4360);
    private static final List<Integer> ONE_MIN_3D_REPEAT_CHECK_METHOD_IDS = Arrays.asList(
            3974, 4050, 4061, 4117, 4163, 4275, 4286, 4291, 4302, 4385, 4390, 4395,
            4405, 4406, 4416, 4417, 4428, 4501, 4563, 4576, 4587, 4683, 5653, 6159,
            6372, 6597, 6598, 6599, 6629, 6630, 6631, 7074, 7176
    );
    private static final Map<String, BigDecimal> TRIAL_LOBBY_LIMITS = new HashMap<>();

    static {
        TRIAL_LOBBY_LIMITS.put("CNY", new BigDecimal("2000"));
        TRIAL_LOBBY_LIMITS.put("USD", new BigDecimal("300"));
        TRIAL_LOBBY_LIMITS.put("JPY", new BigDecimal("30000"));
        TRIAL_LOBBY_LIMITS.put("THB", new BigDecimal("8000"));
        TRIAL_LOBBY_LIMITS.put("VND", new BigDecimal("6000"));
        TRIAL_LOBBY_LIMITS.put("KRW", new BigDecimal("300000"));
        TRIAL_LOBBY_LIMITS.put("IDR", new BigDecimal("4000"));
        TRIAL_LOBBY_LIMITS.put("MYR", new BigDecimal("1000"));
        TRIAL_LOBBY_LIMITS.put("INR", new BigDecimal("30000"));
    }

    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private LotteryMapper lotteryMapper;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private MethodMapper methodMapper;
    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private BlockedMethodMapper blockedMethodMapper;
    @Autowired
    private RoomMasterRelationMapper roomMasterRelationMapper;
    @Autowired
    private UserFundLockTxService userFundLockTxService;
    @Autowired
    private BetOrderTxService betOrderTxService;
    @Autowired
    private BetBonusLimitService betBonusLimitService;

    /**
     * 投注
     * @param req 投注请求
     * @return 投注结果
     */
    @Override
    public ApiResp<BetOrderResp> order(BetOrderReq req) {
        boolean locked = false;
        String title = null;
        String userId = req.getUserId();
        try {
//            if (Boolean.TRUE.equals(req.getOrderFuture())) {
//                return ApiResp.bussError("追号投注暂未实现");
//            }

            /* 创建投注上下文 --取到厅组title信息  */
            BetContext context = buildContext(req);
            title = context.getTitle();
            validateBasicOrder(req, context);

            locked = userFundLockTxService.doLockUserFund(userId, true, WALLET_TYPE_BET, "BET_001", title);
            if (!locked) {
                return ApiResp.bussError("用户资金上锁失败");
            }

            UserFundEntity userFundSum = userFundMapper.selectByUserSum(title, userId);  //取到钱包总计---前台显示01
            UserFundEntity betWallet = userFundMapper.selectByUserAndType(title, userId, WALLET_TYPE_BET); //钱包操作实做--001  walltetype 1
            if (userFundSum == null || betWallet == null) {
                throw new BetBusinessException("未查询到用户钱包");
            }

            BigDecimal totalAmount = req.getLtMoneyAmout();
            if (nvl(userFundSum.getAvailablebalance()).compareTo(totalAmount) < 0) {
                throw new BetBusinessException("余额不足");
            }

            //注单信息收集
            List<TempUserDiffpointsEntity> userDiffpoints = new ArrayList<>();
            List<BetInfoEntity> projects = buildProjects(req, context, userDiffpoints);
            List<ProjectsTmpEntity> projectsTmp = buildProjectsTmp(context, projects);
            BetBonusLimitCheckResult bonusLimitResult = betBonusLimitService.checkAndBuild(context, req, projects);

            //修改账变
            List<OrdersEntity> orders = buildOrders(context, projects, userFundSum);

            betOrderTxService.createOrder(title, userId, WALLET_TYPE_BET, totalAmount,
                    projects, projectsTmp, orders, userDiffpoints,
                    bonusLimitResult.getUserIssueLimits(), bonusLimitResult.getVnBonusLimits());

            context.setProjectList(projects);
            return ApiResp.sucess(buildResponse(req, context, userFundSum, totalAmount));
        } catch (BetBusinessException e) {
            return ApiResp.bussError(e.getMessage());
        } catch (ClientAuthException e) {
            return ApiResp.jwtError(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResp.bussError(e.getMessage());
        } catch (Exception e) {
            log.error("普通投注失败，用户ID={}，彩种ID={}", userId, req.getLotteryId(), e);
            return ApiResp.bussError("投注失败");
        } finally {
            if (locked) {
                if (!userFundLockTxService.doLockUserFund(userId, false, WALLET_TYPE_BET, "BET_001", title)) {
                    log.error("投注流程结束后用户资金解锁失败，用户ID={}，厅主表名={}", userId, title);
                }
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
        ClientUserSession session = ClientUserSessionHolder.getRequired();
        validateRequestSession(req, session);

        RoomMasterEntity roomMaster = roomMasterMapper.selectOne(new LambdaQueryWrapper<RoomMasterEntity>()
                .eq(RoomMasterEntity::getMasterId, Integer.valueOf(req.getRoomMasterId())));
        if (roomMaster == null || !Integer.valueOf(1).equals(roomMaster.getIsActive())) {
            throw new BetBusinessException("厅主不存在或未启用");
        }
        String title = TableNameUtil.safePrefix(roomMaster.getTitle());
        if (!title.equals(session.getRoomMasterTitle())) {
            throw new ClientAuthException("roomMasterTitle mismatch");
        }
        Map<Integer, MethodEntity> methodMap = loadMethodMap(req);
        Integer lotteryId = resolveLotteryId(req, methodMap);
        req.setLotteryId(lotteryId);

        if (!isLotteryInService(roomMaster.getLotteryInService(), req.getLotteryId())) {
            throw new BetBusinessException("彩种未开启");
        }

        UserEntity user = userMapper.selectByUserId(title, req.getUserId());
        validateUser(user, session);
        validateTrialLobbyLimit(roomMaster, user, title, req.getLtMoneyAmout());

        LotteryEntity lottery = lotteryMapper.selectById(Long.valueOf(req.getLotteryId()));
        if (lottery == null || Integer.valueOf(0).equals(lottery.getIsActive())) {
            throw new BetBusinessException("彩种不存在或未启用");
        }

        TempIssueInfoEntity issue = findIssue(title, req);
        validateIssue(issue);

        validateBlockedMethods(roomMaster.getMasterId(), session.getOperator(), methodMap);
        validateShaduizi(roomMaster.getMasterId(), user, methodMap, req);
        validateSpecial3DRules(title, req, issue);

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
     * @param session Token上下文
     */
    private void validateUser(UserEntity user, ClientUserSession session) {
        if (user == null) {
            throw new BetBusinessException("用户不存在");
        }
        if (!normalize(user.getOperator()).equals(normalize(session.getOperator()))) {
            throw new ClientAuthException("用户Token校验失败");
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
     * 校验请求用户与Token上下文一致
     * @param req 投注请求
     * @param session Token上下文
     */
    private void validateRequestSession(BetOrderReq req, ClientUserSession session) {
        if (!normalize(req.getUserId()).equals(normalize(session.getUserId()))) {
            throw new ClientAuthException("you can't access other user's data");
        }
        if (!normalize(req.getRoomMasterId()).equals(String.valueOf(session.getRoomMasterId()))) {
            throw new ClientAuthException("you can't access other room master's data");
        }
    }

    /**
     * 校验测试厅单日投注限额
     * @param roomMaster 厅主
     * @param user 用户
     * @param title 厅主动态表前缀
     * @param currentAmount 本次投注金额
     */
    private void validateTrialLobbyLimit(RoomMasterEntity roomMaster, UserEntity user, String title, BigDecimal currentAmount) {
        Integer businessType = roomMaster.getBusinessType();
        if (!Integer.valueOf(BUSINESS_TYPE_B2B_MW_TEST).equals(businessType)
                && !Integer.valueOf(BUSINESS_TYPE_SINGLE_WALLET_TRIAL).equals(businessType)) {
            return;
        }
        String currency = normalize(user.getCurrency()).toUpperCase();
        BigDecimal limit = TRIAL_LOBBY_LIMITS.get(currency);
        if (limit == null) {
            return;
        }
        BigDecimal todayAmount = betInfoMapper.sumTodayTotalPriceByUser(title, user.getUserId());
        if (nvl(todayAmount).add(nvl(currentAmount)).compareTo(limit) > 0) {
            throw new BetBusinessException("超过测试厅投注限额");
        }
    }

    /**
     * 校验玩法黑名单
     * @param roomMasterId 厅主ID
     * @param operator operator代码
     * @param methodMap 投注玩法
     */
    private void validateBlockedMethods(Integer roomMasterId, String operator, Map<Integer, MethodEntity> methodMap) {
        BlockedMethodEntity blockedMethod = findBlockedMethod(roomMasterId, operator);
        if (blockedMethod == null || !StringUtils.hasText(blockedMethod.getBlockedList())) {
            return;
        }
        JSONObject blockedList;
        try {
            blockedList = JSON.parseObject(blockedMethod.getBlockedList());
        } catch (Exception e) {
            throw new BetBusinessException("玩法黑名单配置错误");
        }
        for (MethodEntity method : methodMap.values()) {
            JSONObject lotteryBlock = blockedList.getJSONObject(String.valueOf(method.getLotteryId()));
            if (lotteryBlock == null) {
                continue;
            }
            if (Boolean.TRUE.equals(lotteryBlock.getBoolean("is_all_close"))) {
                throw new BetBusinessException("彩种已关闭");
            }
            JSONArray methodIds = lotteryBlock.getJSONArray("method_id");
            if (containsJsonValue(methodIds, method.getMethodId())) {
                throw new BetBusinessException("玩法已关闭");
            }
        }
    }

    /**
     * 查询适用的玩法黑名单
     * @param roomMasterId 厅主ID
     * @param operator operator代码
     * @return 黑名单配置
     */
    private BlockedMethodEntity findBlockedMethod(Integer roomMasterId, String operator) {
        String op = normalize(operator);
        if (StringUtils.hasText(op)) {
            BlockedMethodEntity blockedMethod = blockedMethodMapper.selectByRoomMasterIdAndOperator(roomMasterId, op);
            if (blockedMethod != null) {
                return blockedMethod;
            }
        }
        BlockedMethodEntity roomDefault = blockedMethodMapper.selectByRoomMasterIdAndOperator(roomMasterId, "");
        if (roomDefault != null) {
            return roomDefault;
        }
        return blockedMethodMapper.selectByRoomMasterIdAndOperator(0, "");
    }

    /**
     * 校验杀对子白名单
     * @param roomMasterId 厅主ID
     * @param user 用户
     * @param methodMap 玩法映射
     * @param req 投注请求
     */
    private void validateShaduizi(Integer roomMasterId, UserEntity user, Map<Integer, MethodEntity> methodMap, BetOrderReq req) {
        List<Integer> shaduiziMethodIds = new ArrayList<>();
        for (LtProjectReq projectReq : req.getLtProject()) {
            if ("shaduizi".equals(projectReq.getSelectType())) {
                shaduiziMethodIds.add(projectReq.getMethodId());
            }
        }
        if (shaduiziMethodIds.isEmpty()) {
            return;
        }
        RoomMasterRelationEntity relation = roomMasterRelationMapper
                .selectByMasterIdAndCategory(roomMasterId, "methodShaduizi");
        if (relation == null || !StringUtils.hasText(relation.getValue())) {
            throw new BetBusinessException("杀对子已关闭，请重新投注");
        }
        JSONObject config;
        try {
            config = JSON.parseObject(relation.getValue());
        } catch (Exception e) {
            throw new BetBusinessException("杀对子配置错误");
        }
        for (Integer methodId : shaduiziMethodIds) {
            MethodEntity method = methodMap.get(methodId);
            if (method == null) {
                throw new BetBusinessException("杀对子已关闭，请重新投注");
            }
            JSONArray allowed = findShaduiziAllowedMethods(config, normalize(user.getOperator()), method.getLotteryId());
            if (!containsJsonValue(allowed, methodId)) {
                throw new BetBusinessException("杀对子已关闭，请重新投注");
            }
        }
    }

    /**
     * 查询杀对子可用玩法
     * @param config 配置JSON
     * @param operator operator代码
     * @param lotteryId 彩种ID
     * @return 可用玩法列表
     */
    private JSONArray findShaduiziAllowedMethods(JSONObject config, String operator, Integer lotteryId) {
        JSONArray operatorAllowed = null;
        if (StringUtils.hasText(operator)) {
            JSONObject operatorConfig = config.getJSONObject(operator);
            if (operatorConfig != null) {
                operatorAllowed = operatorConfig.getJSONArray(String.valueOf(lotteryId));
            }
        }
        if (operatorAllowed != null && !operatorAllowed.isEmpty()) {
            return operatorAllowed;
        }
        JSONObject defaultConfig = config.getJSONObject("default");
        return defaultConfig == null ? null : defaultConfig.getJSONArray(String.valueOf(lotteryId));
    }

    /**
     * 校验1分3D特殊规则
     * @param title 厅主动态表前缀
     * @param req 投注请求
     * @param issue 奖期
     */
    private void validateSpecial3DRules(String title, BetOrderReq req, TempIssueInfoEntity issue) {
        if (!ONE_MIN_3D_LOTTERY_IDS.contains(req.getLotteryId())) {
            return;
        }
        validateOneMin3DRepeatCodes(req);
        int index = ONE_MIN_3D_LOTTERY_IDS.indexOf(req.getLotteryId());
        Integer qzx3MethodId = ONE_MIN_3D_QZX3_METHOD_IDS.get(index);
        int currentCount = 0;
        for (LtProjectReq projectReq : req.getLtProject()) {
            if (qzx3MethodId.equals(projectReq.getMethodId())) {
                currentCount++;
            }
        }
        if (currentCount <= 0) {
            return;
        }
        Integer existingCount = betInfoMapper.countByUserLotteryIssueMethods(
                title,
                req.getUserId(),
                req.getLotteryId(),
                issue.getIssue(),
                Collections.singletonList(qzx3MethodId)
        );
        if ((existingCount == null ? 0 : existingCount) + currentCount > 5) {
            throw new BetBusinessException("本玩法一期不能投注超过五单");
        }
    }

    /**
     * 校验1分3D部分玩法段内号码不可重复
     * @param req 投注请求
     */
    private void validateOneMin3DRepeatCodes(BetOrderReq req) {
        for (LtProjectReq projectReq : req.getLtProject()) {
            if (!ONE_MIN_3D_REPEAT_CHECK_METHOD_IDS.contains(projectReq.getMethodId())
                    || !StringUtils.hasText(projectReq.getCodes())) {
                continue;
            }
            String codes = projectReq.getCodes().replaceAll("\\s+", "");
            for (String segment : codes.split("\\|")) {
                if (!StringUtils.hasText(segment)) {
                    continue;
                }
                List<String> tokenList = Arrays.asList(segment.split("&"));
                if (tokenList.size() != tokenList.stream().distinct().count()) {
                    throw new BetBusinessException("投注内容有误");
                }
            }
        }
    }

    /**
     * 判断JSON数组是否包含指定值
     * @param array JSON数组
     * @param value 目标值
     * @return 是否包含
     */
    private boolean containsJsonValue(JSONArray array, Integer value) {
        if (array == null || value == null) {
            return false;
        }
        String target = String.valueOf(value);
        for (Object item : array) {
            if (target.equals(String.valueOf(item))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询投注奖期
     * @param title 厅主动态表前缀
     * @param req 投注请求
     * @return 奖期信息
     */
    private TempIssueInfoEntity findIssue(String title, BetOrderReq req) {
        Long lotteryId = Long.valueOf(req.getLotteryId());
        String Issue = StringUtils.hasText(req.getLtProject().get(0).getIssue())
                ? req.getLtProject().get(0).getIssue()
                : req.getLtIssueStart();
        if ("now".equalsIgnoreCase(req.getLtIssueStart())) {
            return tempIssueInfoMapper.selectCurrentByTitle(title, lotteryId);
        }
        //这里取得厅组奖期
        TempIssueInfoEntity issueInfo = tempIssueInfoMapper.selectByTitle(title, lotteryId, Issue);
        if (ObjectUtils.isEmpty(issueInfo)){  //如果厅组奖期号不存在则 取主奖期--并写入厅组奖期
            IssueInfoEntity issueInfoTemp = issueInfoMapper.selectByLotteryIdAndIssue(lotteryId,Issue);
            if (ObjectUtils.isEmpty(issueInfoTemp)){
                return issueInfo;
            }
            issueInfo = tempIssueInfoMapper.insertTempIssueInfo(title,issueInfoTemp);
        }
        return issueInfo;
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
            methodMap.put(method.getMethodId(), method);
        }
        return methodMap;
    }

    /**
     * Resolve lottery id from request or the selected methods.
     */
    private Integer resolveLotteryId(BetOrderReq req, Map<Integer, MethodEntity> methodMap) {
        Integer lotteryId = req.getLotteryId();
        for (LtProjectReq projectReq : req.getLtProject()) {
            MethodEntity method = methodMap.get(projectReq.getMethodId());
            if (method == null || method.getLotteryId() == null) {
                throw new BetBusinessException("玩法彩种配置错误");
            }
            if (lotteryId == null) {
                lotteryId = method.getLotteryId();
                continue;
            }
            if (!lotteryId.equals(method.getLotteryId())) {
                throw new BetBusinessException("玩法和彩种不匹配");
            }
        }
        if (lotteryId == null) {
            throw new BetBusinessException("彩种ID不能为空");
        }
        return lotteryId;
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
    private List<BetInfoEntity> buildProjects(BetOrderReq req, BetContext context,
                                               List<TempUserDiffpointsEntity> userDiffpoints) {
        Date now = new Date();
        JSONObject handicapPrizeJson = loadHandicapPrizeJson(context);
        List<BetInfoEntity> projects = new ArrayList<>();
        for (LtProjectReq projectReq : req.getLtProject()) {
            MethodEntity method = context.getMethodMap().get(projectReq.getMethodId());
            BigDecimal modeRate = modesRate(projectReq.getMode());
            BigDecimal singlePrice = projectReq.getOnePrice()
                    .multiply(BigDecimal.valueOf(projectReq.getNums()))
                    .multiply(modeRate);
            HandicapPrizeSource handicapPrize = resolveHandicapPrizeSource(context, method, handicapPrizeJson);
            BigDecimal point;
            PrizeSource prizeSource;
            if (handicapPrize == null) {
                point = resolveProjectPoint(context, projectReq);
                prizeSource = new PrizeSource(parsePrizeList(resolveRequestPrize(projectReq)), 1);
            } else {
                point = handicapPrize.commissionRate;
                prizeSource = handicapPrize.prizeSource;
            }
            BetPrizeCalc prizeCalc = buildPrizeCalc(projectReq, prizeSource, modeRate, context.getCurrencyRate());

            BetInfoEntity project = new BetInfoEntity();
            project.setProjectId(newBizId(context.getRoomMaster().getMasterId()));
            project.setUserId(req.getUserId());
            project.setPackageId("1");
            project.setTaskId("");
            project.setLotteryId(req.getLotteryId());
            project.setMethodId(projectReq.getMethodId());
            project.setIssue(context.getIssue().getIssue());
            project.setBonus(0D);
            project.setWinbonus(prizeCalc.winbonus);
            project.setCode(projectReq.getCodes());
            project.setCodeType(StringUtils.hasText(projectReq.getType()) ? projectReq.getType() : "digital");
            project.setSinglePrice(singlePrice.doubleValue());
            project.setMultiple(String.valueOf(projectReq.getTimes()));
            project.setTotalPrice(projectReq.getMoney().doubleValue());
            project.setWriteTime(now);
            project.setScode(buildScode(projectReq, prizeCalc));
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
            project.setPointinfo(buildPointInfo(point, projectReq.getMoney(), prizeCalc));
            projects.add(project);

            TempUserDiffpointsEntity userDiffpoint = new TempUserDiffpointsEntity();
            userDiffpoint.setLotteryId(project.getLotteryId());
            userDiffpoint.setIssue(project.getIssue());
            userDiffpoint.setUserId(project.getUserId());
            userDiffpoint.setProjectId(project.getProjectId());
            userDiffpoint.setDiffpoint(new BigDecimal(formatMoney(point)));
            userDiffpoint.setDiffmoney(new BigDecimal(formatMoney(point.multiply(projectReq.getMoney()))));
            userDiffpoint.setStatus(0);
            userDiffpoint.setCancelStatus(0);
            userDiffpoint.setSendtime(null);
            userDiffpoint.setCreatedAt(now);
            userDiffpoint.setUpdatedAt(now);
            userDiffpoints.add(userDiffpoint);
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
     * @param prizeCalc 奖金计算结果
     * @return scode JSON
     */
    private String buildScode(LtProjectReq projectReq, BetPrizeCalc prizeCalc) {
        Map<String, Object> scode = new LinkedHashMap<>();
        scode.put("scode", projectReq.getCodes());
        scode.put("scode_key", projectReq.getScodeKey() == null ? "" : projectReq.getScodeKey());
        scode.put("digitstr", projectReq.getDigitstr() == null ? "" : projectReq.getDigitstr());
        scode.put("oldcode", projectReq.getCodes());
        scode.put("code_name", projectReq.getUpperName() == null ? "" : projectReq.getUpperName());
        scode.put("method_id", projectReq.getMethodId());
        scode.put("selectType", projectReq.getSelectType() == null ? "" : projectReq.getSelectType());
        scode.put("onePrice", projectReq.getOnePrice().toPlainString());
        scode.put("total_bonus", prizeCalc.totalBonusValue());
        scode.put("hprize", prizeCalc.singlePrize);
        scode.put("nums", projectReq.getNums());
        scode.put("codeType", projectReq.getCodeType() == null ? "" : projectReq.getCodeType());
        return JSON.toJSONString(scode);
    }

    /**
     * 组装返点信息 JSON
     * @param point 返点
     * @param money 投注金额
     * @param prizeCalc 奖金计算结果
     * @return pointinfo JSON
     */
    private String buildPointInfo(BigDecimal point, BigDecimal money, BetPrizeCalc prizeCalc) {
        Map<String, Object> pointInfo = new LinkedHashMap<>();
        pointInfo.put("level", prizeCalc.level);
        pointInfo.put("single_prize", prizeCalc.singlePrize);
        pointInfo.put("point", formatMoney(point));
        pointInfo.put("point_price", formatMoney(point.multiply(money)));
        pointInfo.put("prize", prizeCalc.prizeList);
        return JSON.toJSONString(pointInfo);
    }

    /**
     * Calculate project prize fields with the same write-time shape as PHP.
     */
    private BetPrizeCalc buildPrizeCalc(LtProjectReq projectReq, PrizeSource prizeSource, BigDecimal modeRate, BigDecimal currencyRate) {
        List<String> prizeList = prizeSource.prizeList;
        List<String> winbonusList = new ArrayList<>();
        List<String> totalBonusList = new ArrayList<>();
        BigDecimal bonusRate = modeRate
                .multiply(nvl(currencyRate))
                .multiply(projectReq.getOnePrice())
                .multiply(BigDecimal.valueOf(projectReq.getTimes()));
        BigDecimal nums = BigDecimal.valueOf(projectReq.getNums());
        for (String prize : prizeList) {
            BigDecimal winbonus = new BigDecimal(prize).multiply(bonusRate);
            winbonusList.add(formatMoney(winbonus));
            totalBonusList.add(formatMoney(winbonus.multiply(nums)));
        }
        return new BetPrizeCalc(prizeList, prizeSource.level, String.join(",", prizeList),
                String.join(",", winbonusList), totalBonusList);
    }

    private JSONObject loadHandicapPrizeJson(BetContext context) {
        Integer masterId = context.getRoomMaster() == null ? null : context.getRoomMaster().getMasterId();
        String operator = context.getUser() == null ? "" : normalize(context.getUser().getOperator());
        String prizeJson = roomMasterMapper.selectHandicapPrizeJson(masterId, operator);
        if (!StringUtils.hasText(prizeJson)) {
            return null;
        }
        try {
            return JSON.parseObject(prizeJson);
        } catch (Exception e) {
            throw new BetBusinessException("盘口奖金配置错误");
        }
    }

    private HandicapPrizeSource resolveHandicapPrizeSource(BetContext context,
                                                           MethodEntity method,
                                                           JSONObject handicapPrizeJson) {
        if (!isHandicapMethod(context, method)) {
            return null;
        }
        if (method == null || !StringUtils.hasText(method.getPrizeSetKey())) {
            throw new BetBusinessException("盘口玩法配置错误");
        }
        String handicapKey = buildHandicapKey(method.getPrizeSetKey());
        if (handicapPrizeJson == null) {
            throw new BetBusinessException("盘口奖金配置错误");
        }
        JSONObject handicapPrize = handicapPrizeJson.getJSONObject(handicapKey);
        if (handicapPrize == null) {
            throw new BetBusinessException("盘口奖金配置错误");
        }
        String prize = handicapPrize.getString("prize");
        BigDecimal commissionRate = handicapPrize.getBigDecimal("commission_rate");
        if (!StringUtils.hasText(prize) || commissionRate == null) {
            throw new BetBusinessException("盘口奖金配置错误");
        }
        return new HandicapPrizeSource(
                new PrizeSource(parsePrizeList(prize), 1),
                commissionRate.setScale(3, RoundingMode.HALF_UP)
        );
    }

    private String buildHandicapKey(String prizeSetKey) {
        List<String> stack = new ArrayList<>(Arrays.asList(prizeSetKey.split("\\.")));
        if (!stack.isEmpty() && stack.get(0).contains("VN") && stack.size() > 1) {
            stack.remove(stack.size() - 1);
        }
        return String.join(".", stack);
    }

    private String resolveRequestPrize(LtProjectReq projectReq) {
        return StringUtils.hasText(projectReq.getHprize()) ? projectReq.getHprize() : "0";
    }

    /**
     * Normal methods store user base point minus selected point.
     */
    private BigDecimal resolveProjectPoint(BetContext context, LtProjectReq projectReq) {
        BigDecimal selectedPoint = nvl(projectReq.getKeepPoint());
        if (context.getUser() == null || context.getUser().getKeepPoint() == null) {
            return selectedPoint;
        }
        BigDecimal point = context.getUser().getKeepPoint().subtract(selectedPoint);
        return point.compareTo(ZERO) < 0 ? selectedPoint : point;
    }

    private boolean isHandicapMethod(BetContext context, MethodEntity method) {
        if (method != null && isHandicapFunction(method.getPrizeSetKey())) {
            return true;
        }
        if (context == null || context.getLottery() == null) {
            return false;
        }
        return isHandicapFunction(context.getLottery().getFunctionType());
    }

    private boolean isHandicapFunction(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String upper = value.toUpperCase();
        return upper.startsWith("VN_")
                || upper.startsWith("TH")
                || upper.startsWith("STOCK")
                || upper.startsWith("LA")
                || upper.startsWith("MY");
    }

    private List<String> parsePrizeList(String hprize) {
        if (!StringUtils.hasText(hprize)) {
            return Collections.singletonList("0");
        }
        List<String> result = new ArrayList<>();
        for (String item : hprize.split(",")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            try {
                result.add(formatMoney(new BigDecimal(item.trim())));
            } catch (NumberFormatException e) {
                throw new BetBusinessException("奖金格式错误");
            }
        }
        return result.isEmpty() ? Collections.singletonList("0") : result;
    }

    private static class PrizeSource {
        private final List<String> prizeList;
        private final Object level;

        private PrizeSource(List<String> prizeList, Object level) {
            this.prizeList = prizeList;
            this.level = level;
        }
    }

    private static class HandicapPrizeSource {
        private final PrizeSource prizeSource;
        private final BigDecimal commissionRate;

        private HandicapPrizeSource(PrizeSource prizeSource, BigDecimal commissionRate) {
            this.prizeSource = prizeSource;
            this.commissionRate = commissionRate;
        }
    }

    private static class BetPrizeCalc {
        private final List<String> prizeList;
        private final Object level;
        private final String singlePrize;
        private final String winbonus;
        private final List<String> totalBonusList;

        private BetPrizeCalc(List<String> prizeList, Object level, String singlePrize, String winbonus, List<String> totalBonusList) {
            this.prizeList = prizeList;
            this.level = level;
            this.singlePrize = singlePrize;
            this.winbonus = winbonus;
            this.totalBonusList = totalBonusList;
        }

        private Object totalBonusValue() {
            if (totalBonusList.size() == 1) {
                return new BigDecimal(totalBonusList.get(0));
            }
            return String.join(",", totalBonusList);
        }
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
     * 空字符串归一化
     * @param value 原始字符串
     * @return 非空字符串
     */
    private String normalize(String value) {
        return value == null ? "" : value;
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
