package com.giving.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.BonusLimitUserInfoEntity;
import com.giving.entity.BonusLimitUserIssueInfoEntity;
import com.giving.entity.MethodEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.VnBonusLimitEntity;
import com.giving.mapper.BonusLimitMapper;
import com.giving.mapper.VnBonusLimitMapper;
import com.giving.req.BetOrderReq;
import com.giving.req.LtProjectReq;
import com.giving.service.BetBonusLimitService;
import com.giving.service.context.BetBonusLimitCheckResult;
import com.giving.service.context.BetContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BetBonusLimitServiceImpl implements BetBonusLimitService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_BONUS_LIMIT_CNY = new BigDecimal("500000");
    private static final BigDecimal GPI_BONUS_LIMIT_CNY = new BigDecimal("50000");
    private static final Set<Integer> VN_TH_LOTTERY_IDS = unmodifiableSet(
            242,
            162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177,
            178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192,
            193, 194, 195, 196, 197
    );
    private static final Set<String> VN_WHITE_METHOD_CODES = unmodifiableSet(
            "2DT", "2DW", "2DTW", "3DT", "3DW", "3DTW", "4DW"
    );
    private static final Set<String> TH_GROUP_METHOD_CODES = unmodifiableSet("2DTJZX", "3DTJZX");

    @Autowired
    private BonusLimitMapper bonusLimitMapper;
    @Autowired
    private VnBonusLimitMapper vnBonusLimitMapper;

    /**
     * 校验投注奖金限额并生成事务内累计记录
     * @param context 投注上下文
     * @param req 投注请求
     * @param projects 已组装注单
     * @return 奖金限额累计记录
     */
    @Override
    public BetBonusLimitCheckResult checkAndBuild(BetContext context, BetOrderReq req, List<BetInfoEntity> projects) {
        BetBonusLimitCheckResult result = new BetBonusLimitCheckResult();
        BigDecimal prizeLimitCny = resolvePrizeLimitCny(context);
        BigDecimal prizeLimitUserCurrency = scale(prizeLimitCny.multiply(currencyRate(context)));

        validateSingleProjectLimit(projects, prizeLimitUserCurrency);

        List<BonusLimitUserIssueInfoEntity> userIssueRecords = buildUserIssueRecords(context, projects);
        validateUserIssueLimit(context, userIssueRecords);
        result.setUserIssueLimits(userIssueRecords);

        List<VnBonusLimitEntity> vnBonusLimits = buildAndValidateVnBonusLimit(context, req, projects, prizeLimitCny);
        result.setVnBonusLimits(vnBonusLimits);
        return result;
    }

    /**
     * 读取彩种奖金限额，GPI默认上限不允许被放大
     * @param context 投注上下文
     * @return CNY限额
     */
    private BigDecimal resolvePrizeLimitCny(BetContext context) {
        String opCode = context.getUser() == null ? "" : normalize(context.getUser().getOperator());
        Integer lotteryId = context.getLottery() == null ? null : context.getLottery().getLotteryId().intValue();
        BigDecimal dbLimit = bonusLimitMapper.selectLotteryBonusLimit(context.getTitle(), lotteryId, opCode);
        BigDecimal defaultLimit = isGpiRoom(context.getRoomMaster()) ? GPI_BONUS_LIMIT_CNY : DEFAULT_BONUS_LIMIT_CNY;
        if (dbLimit == null || dbLimit.compareTo(ZERO) < 0) {
            return defaultLimit;
        }
        return isGpiRoom(context.getRoomMaster()) && dbLimit.compareTo(defaultLimit) > 0 ? defaultLimit : dbLimit;
    }

    /**
     * 判断是否GPI登入形态
     * @param roomMaster 厅主
     * @return 是否GPI
     */
    private boolean isGpiRoom(RoomMasterEntity roomMaster) {
        return roomMaster != null && Integer.valueOf(1).equals(roomMaster.getLoginType());
    }

    /**
     * 校验单注金额与每个赔率项均不超过最高奖金限额
     * @param projects 注单列表
     * @param prizeLimitUserCurrency 用户币别限额
     */
    private void validateSingleProjectLimit(List<BetInfoEntity> projects, BigDecimal prizeLimitUserCurrency) {
        if (prizeLimitUserCurrency == null || prizeLimitUserCurrency.compareTo(ZERO) <= 0) {
            throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
        }
        for (BetInfoEntity project : projects) {
            BigDecimal totalPrice = BigDecimal.valueOf(project.getTotalPrice());
            if (totalPrice.compareTo(prizeLimitUserCurrency) > 0) {
                throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
            }
            for (BigDecimal winBonus : parseDecimalList(project.getWinbonus())) {
                if (winBonus.compareTo(prizeLimitUserCurrency) > 0) {
                    throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
                }
            }
        }
    }

    /**
     * 生成用户单期奖金累计记录
     * @param context 投注上下文
     * @param projects 注单列表
     * @return 用户单期累计记录
     */
    private List<BonusLimitUserIssueInfoEntity> buildUserIssueRecords(BetContext context, List<BetInfoEntity> projects) {
        Map<String, BonusLimitUserIssueInfoEntity> grouped = new LinkedHashMap<>();
        for (BetInfoEntity project : projects) {
            String codeName = extractCodeName(project.getScode());
            BigDecimal issueBonus = extractProjectTotalBonus(project.getScode());
            String key = limitKey(project.getUserId(), project.getLotteryId(), project.getMethodId(), codeName, project.getIssue());
            BonusLimitUserIssueInfoEntity record = grouped.get(key);
            if (record == null) {
                record = new BonusLimitUserIssueInfoEntity();
                record.setUserId(project.getUserId());
                record.setLotteryId(project.getLotteryId());
                record.setMethodId(project.getMethodId());
                record.setCodeName(codeName);
                record.setIssue(project.getIssue());
                record.setPriceUser(ZERO);
                grouped.put(key, record);
            }
            record.setPriceUser(scale(record.getPriceUser().add(issueBonus)));
        }
        return new ArrayList<>(grouped.values());
    }

    /**
     * 校验用户单期奖金累计是否超过个人限额配置
     * @param context 投注上下文
     * @param records 本次累计记录
     */
    private void validateUserIssueLimit(BetContext context, List<BonusLimitUserIssueInfoEntity> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Integer> methodIds = new ArrayList<>();
        for (BonusLimitUserIssueInfoEntity record : records) {
            if (!methodIds.contains(record.getMethodId())) {
                methodIds.add(record.getMethodId());
            }
        }
        List<BonusLimitUserInfoEntity> configs = bonusLimitMapper.selectUserLimitConfigs(
                context.getTitle(), records.get(0).getUserId(), methodIds);
        Map<String, BigDecimal> configMap = new HashMap<>();
        for (BonusLimitUserInfoEntity config : configs) {
            configMap.put(userConfigKey(config.getMethodId(), normalize(config.getCodeName())),
                    nvl(config.getPriceLimitUser()).multiply(currencyRate(context)));
        }
        for (BonusLimitUserIssueInfoEntity record : records) {
            BigDecimal limit = configMap.get(userConfigKey(record.getMethodId(), normalize(record.getCodeName())));
            if (limit == null || limit.compareTo(ZERO) <= 0) {
                continue;
            }
            BigDecimal current = bonusLimitMapper.selectUserIssuePrice(
                    context.getTitle(),
                    record.getUserId(),
                    record.getLotteryId(),
                    record.getMethodId(),
                    normalize(record.getCodeName()),
                    record.getIssue()
            );
            if (nvl(current).add(nvl(record.getPriceUser())).compareTo(limit) > 0) {
                throw new IllegalStateException("超过用户单期奖金限额");
            }
        }
    }

    /**
     * 校验并生成泰国/越南号码累计奖金记录
     * @param context 投注上下文
     * @param req 投注请求
     * @param projects 注单列表
     * @param prizeLimitCny CNY最高奖金限额
     * @return 号码累计记录
     */
    private List<VnBonusLimitEntity> buildAndValidateVnBonusLimit(BetContext context, BetOrderReq req,
                                                                  List<BetInfoEntity> projects,
                                                                  BigDecimal prizeLimitCny) {
        if (context.getLottery() == null || !VN_TH_LOTTERY_IDS.contains(context.getLottery().getLotteryId().intValue())) {
            return Collections.emptyList();
        }
        String opCode = context.getUser() == null ? "" : normalize(context.getUser().getOperator());
        String issue = context.getIssue() == null ? "" : context.getIssue().getIssue();
        Integer lotteryId = context.getLottery().getLotteryId().intValue();
        List<VnBonusLimitEntity> existing = vnBonusLimitMapper.selectByOpLotteryIssue(
                context.getTitle(), opCode, lotteryId, issue);
        Map<String, VnBonusLimitEntity> existingByCode = new HashMap<>();
        Map<Integer, BigDecimal> methodAmount = new HashMap<>();
        for (VnBonusLimitEntity row : existing) {
            existingByCode.put(vnKey(row.getMethodId(), row.getCodeNumber()), row);
            methodAmount.put(row.getMethodId(), nvl(methodAmount.get(row.getMethodId())).add(nvl(row.getTotalAmount())));
        }

        List<VnBonusLimitEntity> pending = new ArrayList<>();
        Map<String, VnBonusLimitEntity> pendingByCode = new LinkedHashMap<>();
        for (int i = 0; i < projects.size(); i++) {
            BetInfoEntity project = projects.get(i);
            LtProjectReq projectReq = req.getLtProject().get(i);
            MethodEntity method = context.getMethodMap().get(project.getMethodId());
            List<VnBonusLimitEntity> projectRecords = lotteryId == 242
                    ? buildThRecords(context, project, projectReq, method, opCode)
                    : buildVnRecords(context, project, projectReq, method, opCode);
            for (VnBonusLimitEntity record : projectRecords) {
                validateVnQuota(record, existingByCode, methodAmount, pendingByCode, prizeLimitCny);
                mergeVnRecord(pendingByCode, record);
            }
        }
        pending.addAll(pendingByCode.values());
        return pending;
    }

    /**
     * 生成泰国彩号码累计记录
     * @param context 投注上下文
     * @param project 注单
     * @param projectReq 投注项
     * @param method 玩法
     * @param opCode 商户代号
     * @return 累计记录
     */
    private List<VnBonusLimitEntity> buildThRecords(BetContext context, BetInfoEntity project, LtProjectReq projectReq,
                                                    MethodEntity method, String opCode) {
        List<String> codes = splitCodes(projectReq.getCodes());
        if (codes.size() > 1) {
            throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
        }
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        String code = codes.get(0);
        if (method != null && TH_GROUP_METHOD_CODES.contains(normalize(method.getCode()))) {
            List<String> combos = wordCombos(code);
            if (!combos.isEmpty()) {
                code = combos.get(0);
            }
        }
        BigDecimal atoCny = atoCnyRate(context);
        BigDecimal bonusCny = firstWinBonus(project).multiply(atoCny);
        BigDecimal amountCny = nvl(projectReq.getOnePrice()).multiply(BigDecimal.valueOf(projectReq.getTimes())).multiply(atoCny);
        return Collections.singletonList(buildVnRow(context, project, opCode, code, bonusCny, amountCny));
    }

    /**
     * 生成越南彩号码累计记录
     * @param context 投注上下文
     * @param project 注单
     * @param projectReq 投注项
     * @param method 玩法
     * @param opCode 商户代号
     * @return 累计记录
     */
    private List<VnBonusLimitEntity> buildVnRecords(BetContext context, BetInfoEntity project, LtProjectReq projectReq,
                                                    MethodEntity method, String opCode) {
        if (method == null || !VN_WHITE_METHOD_CODES.contains(normalize(method.getCode()))) {
            return Collections.emptyList();
        }
        List<String> codes = splitCodes(projectReq.getCodes());
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        int multiple = 1;
        if (codes.size() != projectReq.getNums()) {
            if (projectReq.getNums() == null || projectReq.getNums() % codes.size() != 0) {
                throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
            }
            multiple = projectReq.getNums() / codes.size();
            if (multiple < 1) {
                throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
            }
        }
        BigDecimal atoCny = atoCnyRate(context);
        List<VnBonusLimitEntity> records = new ArrayList<>();
        for (String code : codes) {
            BigDecimal multiplier = BigDecimal.valueOf(multiple);
            BigDecimal bonusCny = firstWinBonus(project).multiply(multiplier).multiply(atoCny);
            BigDecimal amountCny = nvl(projectReq.getOnePrice())
                    .multiply(BigDecimal.valueOf(projectReq.getTimes()))
                    .multiply(multiplier)
                    .multiply(atoCny);
            records.add(buildVnRow(context, project, opCode, code, bonusCny, amountCny));
        }
        return records;
    }

    /**
     * 检查号码累计额度
     * @param record 本次记录
     * @param existingByCode 已有号码累计
     * @param methodAmount 已有玩法投注累计
     * @param pendingByCode 本请求待写号码累计
     * @param prizeLimitCny CNY最高奖金限额
     */
    private void validateVnQuota(VnBonusLimitEntity record,
                                 Map<String, VnBonusLimitEntity> existingByCode,
                                 Map<Integer, BigDecimal> methodAmount,
                                 Map<String, VnBonusLimitEntity> pendingByCode,
                                 BigDecimal prizeLimitCny) {
        String key = vnKey(record.getMethodId(), record.getCodeNumber());
        VnBonusLimitEntity existing = existingByCode.get(key);
        VnBonusLimitEntity pending = pendingByCode.get(key);
        BigDecimal codeBonus = nvl(existing == null ? null : existing.getTotalBonus())
                .add(nvl(pending == null ? null : pending.getTotalBonus()))
                .add(nvl(record.getTotalBonus()));
        BigDecimal totalAmount = nvl(methodAmount.get(record.getMethodId()));
        for (VnBonusLimitEntity item : pendingByCode.values()) {
            if (record.getMethodId().equals(item.getMethodId())) {
                totalAmount = totalAmount.add(nvl(item.getTotalAmount()));
            }
        }
        totalAmount = totalAmount.add(nvl(record.getTotalAmount()));
        BigDecimal realBonus = totalAmount.subtract(codeBonus);
        if (codeBonus.compareTo(prizeLimitCny) > 0
                && realBonus.compareTo(ZERO) < 0
                && realBonus.abs().compareTo(prizeLimitCny) > 0) {
            throw new IllegalStateException("投注金额超过最大上限，无法完成投注");
        }
    }

    /**
     * 合并本次号码累计记录
     * @param pendingByCode 待写记录
     * @param record 新记录
     */
    private void mergeVnRecord(Map<String, VnBonusLimitEntity> pendingByCode, VnBonusLimitEntity record) {
        String key = vnKey(record.getMethodId(), record.getCodeNumber());
        VnBonusLimitEntity current = pendingByCode.get(key);
        if (current == null) {
            pendingByCode.put(key, record);
            return;
        }
        current.setTotalBonus(scale(nvl(current.getTotalBonus()).add(nvl(record.getTotalBonus()))));
        current.setTotalAmount(scale(nvl(current.getTotalAmount()).add(nvl(record.getTotalAmount()))));
        current.setTotalCount(nvlInt(current.getTotalCount()) + nvlInt(record.getTotalCount()));
    }

    /**
     * 构造号码累计记录
     * @param context 投注上下文
     * @param project 注单
     * @param opCode 商户代号
     * @param code 投注号码
     * @param bonusCny 预计奖金CNY
     * @param amountCny 投注额CNY
     * @return 号码累计记录
     */
    private VnBonusLimitEntity buildVnRow(BetContext context, BetInfoEntity project, String opCode, String code,
                                          BigDecimal bonusCny, BigDecimal amountCny) {
        VnBonusLimitEntity row = new VnBonusLimitEntity();
        row.setOpCode(opCode);
        row.setLotteryId(project.getLotteryId());
        row.setIssue(context.getIssue().getIssue());
        row.setMethodId(project.getMethodId());
        row.setCodeNumber(code);
        row.setTotalBonus(scale(bonusCny));
        row.setTotalAmount(scale(amountCny));
        row.setTotalCount(1);
        return row;
    }

    /**
     * 提取注单预计总奖金，多个赔率项取最大值
     * @param scode 注单scode JSON
     * @return 预计总奖金
     */
    private BigDecimal extractProjectTotalBonus(String scode) {
        JSONObject json = parseJson(scode);
        Object value = json.get("total_bonus");
        if (value == null) {
            return ZERO;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        List<BigDecimal> values = parseDecimalList(String.valueOf(value));
        BigDecimal max = ZERO;
        for (BigDecimal item : values) {
            if (item.compareTo(max) > 0) {
                max = item;
            }
        }
        return max;
    }

    /**
     * 提取信用玩法第四层
     * @param scode 注单scode JSON
     * @return codeName
     */
    private String extractCodeName(String scode) {
        return normalize(parseJson(scode).getString("code_name"));
    }

    /**
     * 解析JSON
     * @param value JSON字符串
     * @return JSON对象
     */
    private JSONObject parseJson(String value) {
        if (!StringUtils.hasText(value)) {
            return new JSONObject();
        }
        return JSON.parseObject(value);
    }

    /**
     * 取得第一个winbonus
     * @param project 注单
     * @return 第一个奖金
     */
    private BigDecimal firstWinBonus(BetInfoEntity project) {
        List<BigDecimal> values = parseDecimalList(project.getWinbonus());
        return values.isEmpty() ? ZERO : values.get(0);
    }

    /**
     * 按逗号解析金额列表
     * @param value 字符串
     * @return 金额列表
     */
    private List<BigDecimal> parseDecimalList(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        List<BigDecimal> result = new ArrayList<>();
        for (String item : value.split(",")) {
            if (StringUtils.hasText(item)) {
                result.add(new BigDecimal(item.trim()));
            }
        }
        return result;
    }

    /**
     * 拆分投注号码
     * @param codes 投注号码
     * @return 号码列表
     */
    private List<String> splitCodes(String codes) {
        if (!StringUtils.hasText(codes)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String item : codes.split(",")) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    /**
     * 生成号码全排列
     * @param code 原号码
     * @return 排序后的排列号码
     */
    private List<String> wordCombos(String code) {
        if (!StringUtils.hasText(code)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        permute("", code, result);
        result = new ArrayList<>(new HashSet<>(result));
        Collections.sort(result);
        return result;
    }

    /**
     * 递归生成排列
     * @param prefix 前缀
     * @param rest 剩余字符
     * @param result 结果
     */
    private void permute(String prefix, String rest, List<String> result) {
        if (rest.length() == 0) {
            result.add(prefix);
            return;
        }
        for (int i = 0; i < rest.length(); i++) {
            permute(prefix + rest.charAt(i), rest.substring(0, i) + rest.substring(i + 1), result);
        }
    }

    /**
     * 用户币别汇率，当前Java投注上下文默认为CNY
     * @param context 投注上下文
     * @return CNY转用户币别倍率
     */
    private BigDecimal currencyRate(BetContext context) {
        BigDecimal rate = context == null ? null : context.getCurrencyRate();
        return rate == null || rate.compareTo(ZERO) <= 0 ? BigDecimal.ONE : rate;
    }

    /**
     * 用户币别转CNY倍率
     * @param context 投注上下文
     * @return 用户币别转CNY倍率
     */
    private BigDecimal atoCnyRate(BetContext context) {
        return BigDecimal.ONE.divide(currencyRate(context), 10, RoundingMode.HALF_UP);
    }

    /**
     * 用户限额配置Key
     * @param methodId 玩法ID
     * @param codeName 第四层
     * @return Key
     */
    private String userConfigKey(Integer methodId, String codeName) {
        return methodId + "|" + normalize(codeName);
    }

    /**
     * 用户单期累计Key
     * @param userId 用户ID
     * @param lotteryId 彩种ID
     * @param methodId 玩法ID
     * @param codeName 第四层
     * @param issue 奖期
     * @return Key
     */
    private String limitKey(String userId, Integer lotteryId, Integer methodId, String codeName, String issue) {
        return userId + "|" + lotteryId + "|" + methodId + "|" + normalize(codeName) + "|" + issue;
    }

    /**
     * VN/TH号码累计Key
     * @param methodId 玩法ID
     * @param codeNumber 号码
     * @return Key
     */
    private String vnKey(Integer methodId, String codeNumber) {
        return methodId + "|" + normalize(codeNumber);
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
     * Integer空值归零
     * @param value 原始值
     * @return 非空整数
     */
    private int nvlInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 字符串空值归一化
     * @param value 原始值
     * @return 非空字符串
     */
    private String normalize(String value) {
        return value == null ? "" : value;
    }

    /**
     * 金额保留6位小数
     * @param value 金额
     * @return 格式化金额
     */
    private BigDecimal scale(BigDecimal value) {
        return nvl(value).setScale(6, RoundingMode.DOWN);
    }

    /**
     * 创建不可变Set
     * @param values 值
     * @param <T> 类型
     * @return 不可变Set
     */
    @SafeVarargs
    private static <T> Set<T> unmodifiableSet(T... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
