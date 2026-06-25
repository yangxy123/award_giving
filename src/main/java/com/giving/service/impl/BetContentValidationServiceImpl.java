package com.giving.service.impl;

import com.giving.entity.MethodEntity;
import com.giving.req.BetOrderReq;
import com.giving.req.LtProjectReq;
import com.giving.service.BetContentValidationService;
import com.giving.service.checker.LotteryBetChecker;
import com.giving.service.context.BetContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BetContentValidationServiceImpl implements BetContentValidationService, LotteryBetChecker {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MAX_PROJECT_COUNT = 800;

    private static final Set<String> SIMPLE_AMOUNT_FUNCTIONS = set(
            "SSC_SIN", "HL_SSC_SIN", "HL_4D_SIN", "K3_SIN", "PK10_SIN", "HL_PK10_SIN",
            "N115_SIN", "HL_N115_SIN", "MARK6_SIN", "KLSF_SIN", "K3_SB", "SSC_LHD",
            "HL_SSC_LHD", "SSC_FT", "PK10_FT", "HL_PK10_FT", "KL_FT", "XY28_FT",
            "KLSF_FT", "K3_YXX", "PK10_BJL"
    );

    private static final Set<String> VN_FUNCTIONS = set("VN_S", "VN_C", "VN_N");
    private static final Set<String> TH_FUNCTIONS = set("TH", "TH_30S", "STOCK", "LA", "MY");
    private static final Set<String> SSC_FUNCTIONS = set("SSC", "HL_SSC", "FC3D", "HL_4D");
    private static final Set<String> N115_FUNCTIONS = set("N115", "HL_N115");
    private static final Set<String> PK10_FUNCTIONS = set("PK10", "HL_PK10");

    private static final Map<Integer, Integer> ZX3_SUM_TABLE = mapOf(
            0, 1, 1, 3, 2, 6, 3, 10, 4, 15, 5, 21, 6, 28, 7, 36, 8, 45,
            9, 55, 10, 63, 11, 69, 12, 73, 13, 75, 14, 75, 15, 73,
            16, 69, 17, 63, 18, 55, 19, 45, 20, 36, 21, 28, 22, 21,
            23, 15, 24, 10, 25, 6, 26, 3, 27, 1
    );
    private static final Map<Integer, Integer> ZU3_SUM_TABLE = mapOf(
            1, 1, 2, 2, 3, 2, 4, 4, 5, 5, 6, 6, 7, 8, 8, 10,
            9, 11, 10, 13, 11, 14, 12, 14, 13, 15, 14, 15, 15, 14,
            16, 14, 17, 13, 18, 11, 19, 10, 20, 8, 21, 6, 22, 5,
            23, 4, 24, 2, 25, 2, 26, 1
    );
    private static final Map<Integer, Integer> ZX2_SUM_TABLE = mapOf(
            0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9,
            9, 10, 10, 9, 11, 8, 12, 7, 13, 6, 14, 5, 15, 4, 16, 3,
            17, 2, 18, 1
    );
    private static final Map<Integer, Integer> ZU2_SUM_TABLE = mapOf(
            0, 0, 1, 1, 2, 1, 3, 2, 4, 2, 5, 3, 6, 3, 7, 4, 8, 4,
            9, 5, 10, 4, 11, 4, 12, 3, 13, 3, 14, 2, 15, 2, 16, 1,
            17, 1, 18, 0
    );

    private static final Map<String, Integer> VN_S_C_MULTIPLIERS = stringMapOf(
            "2DT", 1, "2DW", 1, "2DTW", 2, "2DBZ", 18, "2DBZ7", 7,
            "3DT", 1, "3DW", 1, "3DTW", 2, "3DBZ", 17, "3DBZ7", 7,
            "4DW", 1, "4DBZ", 16, "PL2", 36, "PL3", 54,
            "TST", 25, "TSW", 25, "TSTW", 50, "TSBZ", 450
    );
    private static final Map<String, Integer> VN_N_MULTIPLIERS = stringMapOf(
            "2DT", 4, "2DW", 1, "2DTW", 5, "2DBZ", 27,
            "3DT", 3, "3DW", 1, "3DTW", 4, "3DBZ", 23,
            "4DW", 1, "4DBZ", 20, "PL2", 54, "PL3", 81,
            "TST", 100, "TSW", 25, "TSTW", 125, "TSBZ", 675
    );

    /**
     * 校验投注内容、服务端注数和金额
     * @param req 投注请求
     * @param context 投注上下文
     */
    @Override
    public void validate(BetOrderReq req, BetContext context) {
        if (req.getLtProject() == null || req.getLtProject().isEmpty()) {
            throw new IllegalStateException("投注内容有误");
        }
        if (req.getLtProject().size() >= MAX_PROJECT_COUNT) {
            throw new IllegalStateException("单次投注不能达到或超过800单");
        }

        BigDecimal totalMoney = ZERO;
        long totalNums = 0;
        for (LtProjectReq project : req.getLtProject()) {
            MethodEntity method = context.getMethodMap().get(project.getMethodId());
            if (method == null) {
                throw new IllegalStateException("玩法不存在");
            }
            normalizeProject(context, project);
            long serverNums = calculate(context, project, method);
            if (serverNums <= 0) {
                throw new IllegalStateException("投注内容有误");
            }
            if (project.getNums() == null || serverNums != project.getNums().longValue()) {
                throw new IllegalStateException("投注注数有误");
            }
            validateAmount(context, project, serverNums);
            totalNums += serverNums;
            totalMoney = totalMoney.add(nvl(project.getMoney()));
        }
        if (req.getLtProjectNum() != null && totalNums != req.getLtProjectNum().longValue()) {
            throw new IllegalStateException("投注总注数不一致");
        }
        if (req.getLtMoneyAmout() == null || totalMoney.compareTo(req.getLtMoneyAmout()) != 0) {
            throw new IllegalStateException("投注总金额不一致");
        }
    }

    /**
     * 标准化投注项基础字段
     * @param context 投注上下文
     * @param project 投注项
     */
    private void normalizeProject(BetContext context, LtProjectReq project) {
        if (project.getLotteryId() == null && context.getLottery() != null) {
            project.setLotteryId(context.getLottery().getLotteryId().intValue());
        }
        if (!StringUtils.hasText(project.getIssue()) && context.getIssue() != null) {
            project.setIssue(context.getIssue().getIssue());
        }
        if (!StringUtils.hasText(project.getType())) {
            project.setType("digital");
        }
        if (!StringUtils.hasText(project.getCodes())) {
            throw new IllegalStateException("投注内容有误");
        }
        if ("input".equalsIgnoreCase(project.getType()) && !isVnFunction(functionType(context))) {
            project.setCodes(tryDecodePlainBase64(project.getCodes()));
        }
    }

    /**
     * 计算服务端注数
     * @param context 投注上下文
     * @param project 投注项
     * @param method 玩法配置
     * @return 服务端注数
     */
    @Override
    public long calculate(BetContext context, LtProjectReq project, MethodEntity method) {
        String functionType = functionType(context);
        String code = normalize(method.getCode()).toUpperCase();
        if (SSC_FUNCTIONS.contains(functionType)) {
            return calcSsc(context, project, code);
        }
        if (N115_FUNCTIONS.contains(functionType)) {
            return calcN115(project, code);
        }
        if (PK10_FUNCTIONS.contains(functionType)) {
            return calcPk10(project, code);
        }
        if ("K3".equals(functionType)) {
            return calcK3(project, code);
        }
        if ("KLSF".equals(functionType)) {
            return calcKlsf(project, code);
        }
        if ("KL".equals(functionType)) {
            return calcKl(project, code);
        }
        if ("KLSF_SIN".equals(functionType)) {
            return calcKlsfSin(project, code);
        }
        if (functionType.endsWith("SSC_SIN") || "HL_4D_SIN".equals(functionType)) {
            return calcSscSin(project, code, functionType);
        }
        if ("K3_SIN".equals(functionType)) {
            return calcK3Sin(project, code);
        }
        if ("PK10_SIN".equals(functionType) || "HL_PK10_SIN".equals(functionType)) {
            return calcPk10Sin(project, code);
        }
        if ("N115_SIN".equals(functionType) || "HL_N115_SIN".equals(functionType)) {
            return calcN115Sin(project, code);
        }
        if ("MARK6_SIN".equals(functionType)) {
            return calcMark6Sin(project, code);
        }
        if (isVnFunction(functionType)) {
            return calcVn(project, code, functionType);
        }
        if ("K3_SB".equals(functionType)) {
            return calcK3Sb(project, code);
        }
        if ("SSC_LHD".equals(functionType) || "HL_SSC_LHD".equals(functionType)) {
            return calcSscLhd(project, code);
        }
        if (functionType.endsWith("_FT")) {
            return calcFt(project, code);
        }
        if ("K3_YXX".equals(functionType)) {
            return calcK3Yxx(project, code);
        }
        if (TH_FUNCTIONS.contains(functionType)) {
            return calcTh(project, code);
        }
        if ("PK10_BJL".equals(functionType)) {
            return calcPk10Bjl(project, code);
        }
        return 0;
    }

    /**
     * 校验金额
     * @param context 投注上下文
     * @param project 投注项
     * @param serverNums 服务端注数
     */
    private void validateAmount(BetContext context, LtProjectReq project, long serverNums) {
        if (project.getMode() == null || modeRate(project.getMode()) == null) {
            throw new IllegalStateException("投注模式错误");
        }
        if (project.getOnePrice() == null || project.getOnePrice().compareTo(ZERO) <= 0
                || project.getMoney() == null || project.getMoney().compareTo(ZERO) <= 0) {
            throw new IllegalStateException("投注金额有误");
        }
        if (project.getOnePrice().scale() > 6) {
            throw new IllegalStateException("投注金额有误");
        }
        BigDecimal expected;
        if (isSimpleAmountFunction(functionType(context))) {
            expected = project.getOnePrice().multiply(BigDecimal.valueOf(serverNums));
        } else {
            expected = project.getOnePrice()
                    .multiply(BigDecimal.valueOf(serverNums))
                    .multiply(BigDecimal.valueOf(project.getTimes()))
                    .multiply(modeRate(project.getMode()));
        }
        if (expected.compareTo(project.getMoney()) != 0) {
            throw new IllegalStateException("投注金额有误");
        }
    }

    /**
     * 时时彩/福彩3D/HL 4D 注数
     */
    private long calcSsc(BetContext context, LtProjectReq project, String code) {
        int digits = "HL_4D".equals(functionType(context)) ? 4 : 5;
        if (in(code, "ZX5")) {
            return directPosition(project, 5);
        }
        if (in(code, "ZX4")) {
            return directPosition(project, 4);
        }
        if (in(code, "QZX3", "HZX3", "ZZX3")) {
            return directPosition(project, 3);
        }
        if (in(code, "QZXHZ", "HZXHZ", "ZZXHZ")) {
            return sumTable(project, ZX3_SUM_TABLE, 0, 27);
        }
        if (in(code, "QZUS", "HZUS", "ZZUS")) {
            if (isInput(project)) {
                return countInputGroups(project.getCodes(), 3, true, 0, 9, "onePair");
            }
            int n = countUniqueDigits(project.getCodes());
            return n >= 2 ? n * (n - 1L) : 0;
        }
        if (in(code, "QZUL", "HZUL", "ZZUL")) {
            if (isInput(project)) {
                return countInputGroups(project.getCodes(), 3, true, 0, 9, "allUnique");
            }
            int n = countUniqueDigits(project.getCodes());
            if (n < 3 || n > 9) {
                return 0;
            }
            return combination(n, 3);
        }
        if (in(code, "QZUHZ", "HZUHZ", "ZZUHZ")) {
            return sumTable(project, ZU3_SUM_TABLE, 1, 26);
        }
        if (in(code, "QZX2", "HZX2")) {
            return directTwo(project, "shaduizi".equals(project.getSelectType()));
        }
        if (in(code, "ZXHZ2")) {
            return sumTable(project, ZX2_SUM_TABLE, 0, 18);
        }
        if (in(code, "QZU2", "HZU2")) {
            if (isInput(project)) {
                return countInputGroups(project.getCodes(), 2, true, 0, 9, "allUnique");
            }
            int n = countUniqueDigits(project.getCodes());
            return n >= 2 ? combination(n, 2) : 0;
        }
        if (in(code, "ZUHZ2")) {
            return sumTable(project, ZU2_SUM_TABLE, 0, 18);
        }
        if (in(code, "ZU3BD")) {
            return countUniqueDigits(project.getCodes()) == 1 ? 54 : 0;
        }
        if (in(code, "ZU2BD")) {
            return countUniqueDigits(project.getCodes()) == 1 ? 9 : 0;
        }
        if (in(code, "HBDW1", "BDW1", "HSCS", "SXBX", "SJFC")) {
            return countUniqueDigits(project.getCodes());
        }
        if (in(code, "HBDW2", "WXEMBDW")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 2 ? combination(n, 2) : 0;
        }
        if (in(code, "WXSMBDW")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 3 ? combination(n, 3) : 0;
        }
        if (in(code, "DWD")) {
            return sumSegmentsDigits(project.getCodes(), digits);
        }
        if (in(code, "DWD3")) {
            return sumSegmentsDigits(project.getCodes(), 3);
        }
        if (in(code, "QDXDS", "HDXDS")) {
            return productAllowedSegments(project.getCodes(), 2, set("0", "1", "2", "3"));
        }
        if (in(code, "SSCRX2ZXFS")) {
            return rxDirectFs(project.getCodes(), 2, "shaduizi".equals(project.getSelectType()));
        }
        if (in(code, "SSCRX2ZXDS", "SSCRX2ZUDS")) {
            long groups = countInputGroups(project.getCodes(), 2, "SSCRX2ZUDS".equals(code) || "shaduizi".equals(project.getSelectType()), 0, 9, "allUnique");
            int p = digitstrCount(project.getDigitstr());
            return p >= 2 ? groups * combination(p, 2) : 0;
        }
        if (in(code, "SSCRX2ZUFS")) {
            int n = countUniqueDigits(project.getCodes());
            int p = digitstrCount(project.getDigitstr());
            return n >= 2 && p >= 2 ? combination(n, 2) * combination(p, 2) : 0;
        }
        if (in(code, "SSCRX3ZXFS")) {
            return rxDirectFs(project.getCodes(), 3, false);
        }
        if (in(code, "SSCRX3ZXDS")) {
            long groups = countInputGroups(project.getCodes(), 3, false, 0, 9, "digits");
            return groups * rxMultiplier(digitstrCount(project.getDigitstr()), 3);
        }
        if (in(code, "SSCRX3ZS")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 2 ? n * (n - 1L) * rxMultiplier(digitstrCount(project.getDigitstr()), 3) : 0;
        }
        if (in(code, "SSCRX3ZL")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 3 ? combination(n, 3) * rxMultiplier(digitstrCount(project.getDigitstr()), 3) : 0;
        }
        if (in(code, "SSCRX4ZXFS")) {
            return rxDirectFs(project.getCodes(), 4, false);
        }
        if (in(code, "SSCRX4ZXDS")) {
            long groups = countInputGroups(project.getCodes(), 4, false, 0, 9, "digits");
            int p = digitstrCount(project.getDigitstr());
            return p == 5 ? groups * 5 : p == 4 ? groups : 0;
        }
        if (in(code, "SXZU24")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 4 ? combination(n, 4) : 0;
        }
        if (in(code, "SXZU12")) {
            return twoSegmentGroup(project.getCodes(), 1, 2, 1);
        }
        if (in(code, "SXZU6")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 2 ? combination(n, 2) : 0;
        }
        if (in(code, "SXZU4")) {
            return twoSegmentProductMinusIntersection(project.getCodes(), 1, 1);
        }
        if (in(code, "WXZU120")) {
            int n = countUniqueDigits(project.getCodes());
            return n >= 5 ? combination(n, 5) : 0;
        }
        if (in(code, "WXZU60")) {
            return twoSegmentGroup(project.getCodes(), 1, 3, 2);
        }
        if (in(code, "WXZU30")) {
            List<Set<String>> box = segmentSets(project.getCodes(), 2, digitSet());
            if (box.size() != 2 || box.get(0).size() < 2 || box.get(1).isEmpty()) {
                return 0;
            }
            int a = box.get(0).size();
            int b = box.get(1).size();
            int inter = intersectionSize(box.get(0), box.get(1));
            return combination(a, 2) * b - inter * (a - 1L);
        }
        if (in(code, "WXZU20")) {
            return twoSegmentGroup(project.getCodes(), 1, 2, 1);
        }
        if (in(code, "WXZU10", "WXZU5")) {
            return twoSegmentProductMinusIntersection(project.getCodes(), 1, 1);
        }
        if (in(code, "LMPYZ", "LMPEZ", "LMPSZ", "LMPLHH")) {
            return calcSscLm(project, code, digits);
        }
        return 0;
    }

    /**
     * 11选5 注数
     */
    private long calcN115(LtProjectReq project, String code) {
        if (in(code, "SDZX3")) {
            return isInput(project) ? countInputNumberGroups(project.getCodes(), 3, 1, 11, true)
                    : distinctCartesian(project.getCodes(), 3, 1, 11);
        }
        if (in(code, "SDZU3")) {
            return isInput(project) ? countInputNumberGroups(project.getCodes(), 3, 1, 11, true)
                    : combination(countUniqueNumbers(project.getCodes(), 1, 11), 3);
        }
        if (in(code, "SDZX2")) {
            return isInput(project) ? countInputNumberGroups(project.getCodes(), 2, 1, 11, true)
                    : distinctCartesian(project.getCodes(), 2, 1, 11);
        }
        if (in(code, "SDZU2")) {
            return isInput(project) ? countInputNumberGroups(project.getCodes(), 2, 1, 11, true)
                    : combination(countUniqueNumbers(project.getCodes(), 1, 11), 2);
        }
        if (in(code, "SDBDW")) {
            return countUniqueNumbers(project.getCodes(), 1, 11);
        }
        if (in(code, "SDDWD")) {
            return sumSegmentNumbers(project.getCodes(), 3, 1, 11);
        }
        if (in(code, "SDDDS")) {
            return countAllowed(project.getCodes(), set("0", "1", "2", "3", "4", "5"));
        }
        if (in(code, "SDCZW")) {
            return countUniqueNumbers(project.getCodes(), 3, 9);
        }
        if (code.startsWith("SDRX")) {
            int k = parseTrailingInt(code.substring(4), 1);
            if (isInput(project)) {
                return countInputNumberGroups(project.getCodes(), k, 1, 11, true);
            }
            return combination(countUniqueNumbers(project.getCodes(), 1, 11), k);
        }
        if (code.startsWith("LTRXDT")) {
            int k = parseTrailingInt(code.substring(6), 1);
            return danTuo(project.getCodes(), k, 1, 11);
        }
        return 0;
    }

    /**
     * PK10 官方盘注数
     */
    private long calcPk10(LtProjectReq project, String code) {
        if (code.endsWith("_QY") || "QY".equals(code)) {
            return countUniqueNumbers(project.getCodes(), 1, 10);
        }
        if (code.endsWith("_QE2") || "QE2".equals(code)) {
            return isInput(project) ? countInputNumberGroups(project.getCodes(), 2, 1, 10, true)
                    : distinctCartesian(project.getCodes(), 2, 1, 10);
        }
        if (code.endsWith("_QS3") || "QS3".equals(code)) {
            return isInput(project) ? countInputNumberGroups(project.getCodes(), 3, 1, 10, true)
                    : distinctCartesian(project.getCodes(), 3, 1, 10);
        }
        if (code.endsWith("_DXDS") || "DXDS".equals(code)) {
            return sumAllowedSegments(project.getCodes(), 10, set("0", "1", "2", "3"));
        }
        if (code.endsWith("_OTF") || code.endsWith("_STT") || in(code, "OTF", "STT")) {
            return sumSegmentNumbers(project.getCodes(), 5, 1, 10);
        }
        if (in(code, "LHD")) {
            return sumAllowedSegments(project.getCodes(), 5, set("0", "1", "6", "7"));
        }
        return 0;
    }

    /**
     * 快3官方盘注数
     */
    private long calcK3(LtProjectReq project, String code) {
        List<String> items = items(project.getCodes());
        if (in(code, "STH")) {
            long count = 0;
            for (String item : items) {
                if (item.matches("[1-6]{3}") && item.charAt(0) == item.charAt(1) && item.charAt(1) == item.charAt(2)) {
                    count++;
                }
            }
            return count;
        }
        if (in(code, "SBTH")) {
            return combination(countUniqueNumbers(project.getCodes(), 1, 6), 3);
        }
        if (in(code, "DX")) {
            return countK3AdjacentPair(items);
        }
        if (in(code, "FX")) {
            return countPairs(items, true, 1, 6);
        }
        if (in(code, "EBTH")) {
            return countPairs(items, false, 1, 6);
        }
        if (in(code, "HZ")) {
            return countUniqueNumbers(project.getCodes(), 3, 18);
        }
        if (in(code, "CYGH")) {
            return countUniqueNumbers(project.getCodes(), 1, 6);
        }
        return 0;
    }

    /**
     * 快乐十分官方盘注数
     */
    private long calcKlsf(LtProjectReq project, String code) {
        if (in(code, "DWD")) {
            return sumSegmentNumbers(project.getCodes(), 8, 1, 20);
        }
        if (code.matches("[1-5]Z[1-5]")) {
            int k = code.charAt(0) - '0';
            return combination(countUniqueNumbers(project.getCodes(), 1, 20), k);
        }
        if (in(code, "XELZHI")) {
            return distinctCartesian(project.getCodes(), 2, 1, 20);
        }
        if (in(code, "XELZU")) {
            return combination(countUniqueNumbers(project.getCodes(), 1, 20), 2);
        }
        if (in(code, "XSQZHI", "XSHZHI")) {
            return distinctCartesian(project.getCodes(), 3, 1, 20);
        }
        if (in(code, "XSQZU", "XSHZU")) {
            return combination(countUniqueNumbers(project.getCodes(), 1, 20), 3);
        }
        if (in(code, "DXDS")) {
            List<Set<String>> box = segmentSets(project.getCodes(), 9, set("0", "1", "2", "3", "4", "5", "6", "7"));
            if (box.size() != 9 || !box.get(0).stream().allMatch(set("0", "1", "2")::contains)) {
                return 0;
            }
            return sumSizes(box);
        }
        if (in(code, "LH")) {
            return sumAllowedSegments(project.getCodes(), 5, set("0", "1", "6", "7"));
        }
        if (in(code, "QW")) {
            return sumAllowedSegments(project.getCodes(), 8,
                    set("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"));
        }
        return 0;
    }

    /**
     * 北京快乐8注数
     */
    private long calcKl(LtProjectReq project, String code) {
        if (code.matches("RX[1-5]")) {
            int k = code.charAt(2) - '0';
            return combination(countUniqueNumbers(project.getCodes(), 1, 80), k);
        }
        if (in(code, "SXP", "QOP", "HZDX")) {
            return countAllowed(project.getCodes(), set("0", "1", "2"));
        }
        if (in(code, "HZDS")) {
            return countAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "HZDXDS")) {
            return countAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "WX")) {
            return countAllowed(project.getCodes(), set("0", "1", "2", "3", "4"));
        }
        return 0;
    }

    /**
     * 快乐十分信用盘注数
     */
    private long calcKlsfSin(LtProjectReq project, String code) {
        if (code.matches("D[1-8]W(DX|DS|HDS)") || in(code, "ZHDX", "ZHDS", "ZHWDX")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (code.matches("D[1-8]WDW")) {
            return singleNumber(project.getCodes(), 1, 20);
        }
        if (code.matches("D[1-8]WSJ") || code.matches("D[1-8]WFW")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (code.matches("D[1-8]WWH")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3", "4"));
        }
        if (code.matches("D[1-8]WZFB")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2"));
        }
        if (in(code, "RX2", "X2LZHI", "X2LZHU")) {
            return exactNumbers(project.getCodes(), 2, 1, 20) ? 1 : 0;
        }
        if (in(code, "RX3", "X3QZHI", "X3QZHU")) {
            return exactNumbers(project.getCodes(), 3, 1, 20) ? 1 : 0;
        }
        if (in(code, "RX4")) {
            return exactNumbers(project.getCodes(), 4, 1, 20) ? 1 : 0;
        }
        if (in(code, "RX5")) {
            return exactNumbers(project.getCodes(), 5, 1, 20) ? 1 : 0;
        }
        if (code.matches("[1-7]VS[1-8]")) {
            return singleAllowed(project.getCodes(), set("0", "1", "6", "7"));
        }
        return 0;
    }

    /**
     * 时时彩信用盘注数
     */
    private long calcSscSin(LtProjectReq project, String code, String functionType) {
        if (in(code, "WDW", "QDW", "BDW", "SDW", "GDW", "YZZH", "QSKU", "ZSKU", "HSKU")) {
            return singleNumber(project.getCodes(), 0, 9);
        }
        if (code.endsWith("DX") || code.endsWith("DS") || code.endsWith("ZH") || in(code, "ZHDX", "ZHDS")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3", "4", "5"));
        }
        if (in(code, "DN")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A"));
        }
        if (in(code, "NDX")) {
            return singleAllowed(project.getCodes(), set("B", "C"));
        }
        if (in(code, "NDS")) {
            return singleAllowed(project.getCodes(), set("D", "E"));
        }
        if (in(code, "SH")) {
            return singleAllowed(project.getCodes(), "HL_4D_SIN".equals(functionType)
                    ? set("0", "1", "2", "3", "4", "5")
                    : set("0", "1", "2", "3", "4", "5", "6", "7"));
        }
        if (in(code, "WQ", "WB", "WS", "WG", "QB", "QS", "QG", "BS", "BG", "SG")) {
            return singleAllowed(project.getCodes(), set("6", "7", "8", "0", "1", "2"));
        }
        return 0;
    }

    /**
     * 快3信用盘注数
     */
    private long calcK3Sin(LtProjectReq project, String code) {
        if (in(code, "SJ", "WS")) {
            return singleNumber(project.getCodes(), 1, 6);
        }
        if (in(code, "DX", "DSH")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "QS")) {
            return "全骰".equals(project.getCodes()) ? 1 : 0;
        }
        if (in(code, "DP")) {
            return countPairs(items(project.getCodes()), true, 1, 6) == 1 ? 1 : 0;
        }
        if (in(code, "CP")) {
            return cpDice(project.getCodes()) ? 1 : 0;
        }
        if (in(code, "DS")) {
            String value = project.getCodes().replace("点", "");
            return isIntInRange(value, 4, 17) ? 1 : 0;
        }
        return 0;
    }

    /**
     * PK10信用盘注数
     */
    private long calcPk10Sin(LtProjectReq project, String code) {
        if (in(code, "GYJHDX")) {
            return singleAllowed(project.getCodes(), set("0", "1"));
        }
        if (in(code, "GYJHDS")) {
            return singleAllowed(project.getCodes(), set("2", "3"));
        }
        if (code.endsWith("DX")) {
            return singleAllowed(project.getCodes(), set("0", "1"));
        }
        if (code.endsWith("DS")) {
            return singleAllowed(project.getCodes(), set("2", "3"));
        }
        if (code.endsWith("LH")) {
            return singleAllowed(project.getCodes(), set("6", "7", "0", "1"));
        }
        if (in(code, "GYHZ")) {
            return singleNumber(project.getCodes(), 3, 19);
        }
        if (in(code, "GYZH")) {
            return pk10Gyzh(project.getCodes()) ? 1 : 0;
        }
        if (in(code, "FTF", "FTN", "FTJ", "FTZ")) {
            return ftCode(project.getCodes(), code);
        }
        if (in(code, "MSX")) {
            return singleAllowed(project.getCodes(), set("闲一", "闲二", "闲三"));
        }
        return singleNumber(project.getCodes(), 1, 10);
    }

    /**
     * 11选5信用盘注数
     */
    private long calcN115Sin(LtProjectReq project, String code) {
        if (code.matches("D[1-5]WDX")) {
            return singleAllowed(project.getCodes(), set("0", "1"));
        }
        if (code.matches("D[1-5]WDS")) {
            return singleAllowed(project.getCodes(), set("2", "3"));
        }
        if (code.matches("D[1-5]WDW")) {
            return singleNumber(project.getCodes(), 1, 11);
        }
        if (in(code, "ZHZDX")) {
            return singleAllowed(project.getCodes(), set("0", "1"));
        }
        if (in(code, "ZHZDS")) {
            return singleAllowed(project.getCodes(), set("2", "3"));
        }
        if (in(code, "ZHZWDX")) {
            return singleAllowed(project.getCodes(), set("4", "5"));
        }
        if (in(code, "ZHSX")) {
            return singleAllowed(project.getCodes(), set("6", "7", "8"));
        }
        if (in(code, "ZHJO")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2"));
        }
        if (in(code, "RX1Z1")) {
            return singleNumber(project.getCodes(), 1, 11);
        }
        if (code.matches("RX[2-8]Z[2-5]")) {
            int k = code.charAt(2) - '0';
            return combination(countUniqueNumbers(project.getCodes(), 1, 11), k);
        }
        if (code.matches("D[1-5]WVSD[1-5]W")) {
            return singleAllowed(project.getCodes(), set("6", "7", "0", "1"));
        }
        return 0;
    }

    /**
     * MARK6信用盘注数
     */
    private long calcMark6Sin(LtProjectReq project, String code) {
        if (in(code, "ZHDX", "ZHDS", "JO", "SX")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "ZHDXDS", "QSB")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "TM", "ZM") || code.matches("ZM[1-6]")) {
            return singleNumber(project.getCodes(), 1, 49);
        }
        if (code.matches("(TM|ZM[1-6]).*(DX|DS|HDX|HDS|WDX|HWDX|JQYS|SB)")
                || in(code, "BB", "WS")) {
            return singleAllowed(project.getCodes(), rangeSet(0, 18));
        }
        if (in(code, "TX", "YX")) {
            return singleNumber(project.getCodes(), 0, 11);
        }
        if (code.matches("X\\d{1,2}Z") || code.matches("X\\d{1,2}BZ")) {
            int k = numberBetween(code, "X", code.endsWith("BZ") ? "BZ" : "Z");
            return exactNumbers(project.getCodes(), k, 0, 11) ? 1 : 0;
        }
        if (code.matches("X[2-5]")) {
            int k = code.charAt(1) - '0';
            return mark6ExactOrDt(project.getCodes(), k, 0, 11) ? 1 : 0;
        }
        if (code.matches("WZ[2-4]")) {
            int k = code.charAt(2) - '0';
            return mark6ExactOrDt(project.getCodes(), k, 0, 9) ? 1 : 0;
        }
        if (code.matches("BZ(5|6|7|8|9|10|11|12)")) {
            int k = Integer.parseInt(code.substring(2));
            return exactNumbers(project.getCodes(), k, 1, 49) ? 1 : 0;
        }
        if (in(code, "SQZ", "SZE")) {
            return mark6ExactOrDt(project.getCodes(), 3, 1, 49) ? 1 : 0;
        }
        if (in(code, "EQZ", "EZT", "TC")) {
            return mark6ExactOrDt(project.getCodes(), 2, 1, 49) ? 1 : 0;
        }
        if (code.matches("ZM[1-6]VS(ZM[1-6]|TM)")) {
            return singleAllowed(project.getCodes(), set("0", "1", "6", "7"));
        }
        return 0;
    }

    /**
     * 越南彩注数
     */
    private long calcVn(LtProjectReq project, String code, String functionType) {
        Map<String, Integer> map = "VN_N".equals(functionType) ? VN_N_MULTIPLIERS : VN_S_C_MULTIPLIERS;
        Integer multiplier = map.get(code);
        if (multiplier == null) {
            return 0;
        }
        if (in(code, "PL2")) {
            long groups = vnPairGroups(project, 2);
            return groups > 0 ? groups * multiplier : 0;
        }
        if (in(code, "PL3")) {
            long groups = vnPairGroups(project, 3);
            return groups > 0 ? groups * multiplier : 0;
        }
        if (code.startsWith("TS")) {
            List<String> values = items(project.getCodes());
            if (values.isEmpty()) {
                return 0;
            }
            long nums = 0;
            for (String item : values) {
                if (!isIntInRange(item, 0, 13)) {
                    return 0;
                }
                nums += Integer.parseInt(item) < 6 ? multiplier * 2L : multiplier;
            }
            return nums;
        }
        int len = code.startsWith("2") ? 2 : code.startsWith("3") ? 3 : code.startsWith("4") ? 4 : 0;
        if (len == 0) {
            return 0;
        }
        List<String> groups = codeGroups(project.getCodes());
        if (groups.size() > 5000 && in(code, "2DW", "3DW", "4DW", "2DBZ", "3DBZ", "4DBZ", "PL3")) {
            throw new IllegalStateException("单式超过5000组限制");
        }
        for (String group : groups) {
            if (!group.matches("\\d{" + len + "}")) {
                return 0;
            }
        }
        return groups.size() * (long) multiplier;
    }

    /**
     * K3骰宝注数
     */
    private long calcK3Sb(LtProjectReq project, String code) {
        if (in(code, "YM", "BZ", "WS")) {
            return singleNumber(project.getCodes(), 1, 6);
        }
        if (in(code, "DX", "DS")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "BZTX")) {
            return "豹子通选".equals(project.getCodes()) ? 1 : 0;
        }
        if (in(code, "DZ")) {
            return countPairs(items(project.getCodes()), true, 1, 6) == 1 ? 1 : 0;
        }
        if (in(code, "EM")) {
            return countPairs(items(project.getCodes()), false, 1, 6) == 1 ? 1 : 0;
        }
        if (in(code, "HZ")) {
            return singleNumber(project.getCodes(), 4, 17);
        }
        return 0;
    }

    /**
     * 龙虎斗注数
     */
    private long calcSscLhd(LtProjectReq project, String code) {
        if (in(code, "LDX", "LDS", "HDX", "HDS")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3"));
        }
        if (in(code, "LHD")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2"));
        }
        if (in(code, "LDW", "HDW")) {
            return singleNumber(project.getCodes(), 0, 9);
        }
        if (in(code, "WX")) {
            return singleAllowed(project.getCodes(), set("0", "1", "2", "3", "4"));
        }
        return 0;
    }

    /**
     * 番摊注数
     */
    private long calcFt(LtProjectReq project, String code) {
        if (in(code, "DX")) {
            return singleAllowed(project.getCodes(), set("0", "1"));
        }
        if (in(code, "DS")) {
            return singleAllowed(project.getCodes(), set("2", "3"));
        }
        if (in(code, "FTF", "FTN", "FTJ", "FTZ")) {
            return ftCode(project.getCodes(), code);
        }
        return 0;
    }

    /**
     * 鱼虾蟹注数
     */
    private long calcK3Yxx(LtProjectReq project, String code) {
        if (in(code, "QW")) {
            return "全围".equals(project.getCodes()) ? 1 : 0;
        }
        if (in(code, "WS", "BZ", "SS", "DS")) {
            return singleNumber(project.getCodes(), 1, 6);
        }
        if (in(code, "LKSZ")) {
            return countPairs(items(project.getCodes()), false, 1, 6) == 1 ? 1 : 0;
        }
        return 0;
    }

    /**
     * 泰国/股票/老挝/缅甸类注数
     */
    private long calcTh(LtProjectReq project, String code) {
        int len;
        if (in(code, "1DT", "1DW")) {
            len = 1;
        } else if (in(code, "2DT", "2DW", "2DTJZX")) {
            len = 2;
        } else if (in(code, "3DT", "3DTJZX", "3DQS", "3DHS")) {
            len = 3;
        } else {
            return 0;
        }
        List<String> values = uniqueItems(project.getCodes());
        return values.size() == 1 && values.get(0).matches("\\d{" + len + "}") ? 1 : 0;
    }

    /**
     * PK10百家乐注数
     */
    private long calcPk10Bjl(LtProjectReq project, String code) {
        if (in(code, "ZXH")) {
            return singleAllowed(project.getCodes(), set("庄", "闲", "和"));
        }
        if (in(code, "DZ")) {
            return singleAllowed(project.getCodes(), set("庄对", "闲对"));
        }
        if (in(code, "DX")) {
            return singleAllowed(project.getCodes(), set("大", "小", "0", "1"));
        }
        return 0;
    }

    private long directPosition(LtProjectReq project, int positionCount) {
        if (isInput(project)) {
            return countInputGroups(project.getCodes(), positionCount, false, 0, 9, "digits");
        }
        List<Set<String>> segments = segmentSets(project.getCodes(), positionCount, digitSet());
        return segments.size() == positionCount ? productSizes(segments) : 0;
    }

    private long directTwo(LtProjectReq project, boolean excludePair) {
        if (isInput(project)) {
            return countInputGroups(project.getCodes(), 2, excludePair, 0, 9, excludePair ? "allUnique" : "digits");
        }
        List<Set<String>> box = segmentSets(project.getCodes(), 2, digitSet());
        if (box.size() != 2) {
            return 0;
        }
        long count = box.get(0).size() * (long) box.get(1).size();
        return excludePair ? count - intersectionSize(box.get(0), box.get(1)) : count;
    }

    private long calcSscLm(LtProjectReq project, String code, int digits) {
        int segments;
        Set<String> allowed;
        if ("LMPYZ".equals(code)) {
            segments = digits == 4 ? 4 : 6;
            allowed = rangeSet(0, 5);
        } else if ("LMPEZ".equals(code)) {
            segments = digits == 4 ? 6 : 10;
            allowed = rangeSet(0, 5);
        } else if ("LMPSZ".equals(code)) {
            segments = digits == 4 ? 4 : 6;
            allowed = rangeSet(0, 5);
        } else {
            segments = digits == 4 ? 6 : 10;
            allowed = set("6", "7", "8", "0", "1", "2");
        }
        return sumAllowedSegments(project.getCodes(), segments, allowed);
    }

    private long sumTable(LtProjectReq project, Map<Integer, Integer> table, int min, int max) {
        long nums = 0;
        for (String item : uniqueItems(project.getCodes())) {
            if (!isIntInRange(item, min, max)) {
                return 0;
            }
            nums += table.getOrDefault(Integer.parseInt(item), 0);
        }
        return nums;
    }

    private long twoSegmentGroup(String codes, int minFirst, int minSecond, int chooseSecond) {
        List<Set<String>> box = segmentSets(codes, 2, digitSet());
        if (box.size() != 2 || box.get(0).size() < minFirst || box.get(0).size() > 9 || box.get(1).size() < minSecond) {
            return 0;
        }
        int inter = intersectionSize(box.get(0), box.get(1));
        return box.get(0).size() * combination(box.get(1).size(), chooseSecond)
                - inter * combination(box.get(1).size() - 1, chooseSecond - 1);
    }

    private long twoSegmentProductMinusIntersection(String codes, int minFirst, int minSecond) {
        List<Set<String>> box = segmentSets(codes, 2, digitSet());
        if (box.size() != 2 || box.get(0).size() < minFirst || box.get(1).size() < minSecond) {
            return 0;
        }
        return box.get(0).size() * (long) box.get(1).size() - intersectionSize(box.get(0), box.get(1));
    }

    private long rxDirectFs(String codes, int choose, boolean excludePair) {
        List<Set<String>> box = segmentSets(codes, -1, digitSet());
        if (box.size() < choose) {
            return 0;
        }
        return sumPositionProducts(box, choose, excludePair);
    }

    private long sumPositionProducts(List<Set<String>> box, int choose, boolean excludePair) {
        return sumPositionProducts(box, choose, 0, new ArrayList<>(), excludePair);
    }

    private long sumPositionProducts(List<Set<String>> box, int choose, int start, List<Set<String>> selected, boolean excludePair) {
        if (selected.size() == choose) {
            if (excludePair && choose == 2) {
                return selected.get(0).size() * (long) selected.get(1).size()
                        - intersectionSize(selected.get(0), selected.get(1));
            }
            return productSizes(selected);
        }
        long total = 0;
        for (int i = start; i < box.size(); i++) {
            if (box.get(i).isEmpty()) {
                continue;
            }
            selected.add(box.get(i));
            total += sumPositionProducts(box, choose, i + 1, selected, excludePair);
            selected.remove(selected.size() - 1);
        }
        return total;
    }

    private long rxMultiplier(int p, int choose) {
        if (choose == 3) {
            if (p == 5) {
                return 10;
            }
            if (p == 4) {
                return 4;
            }
            return p == 3 ? 1 : 0;
        }
        return 0;
    }

    private long danTuo(String codes, int k, int min, int max) {
        List<String> parts = Arrays.asList(clean(codes).split("\\|", -1));
        if (parts.size() != 2) {
            return 0;
        }
        Set<String> dan = numbersSet(parts.get(0), min, max);
        Set<String> tuo = numbersSet(parts.get(1), min, max);
        if (dan.isEmpty() || dan.size() >= k || tuo.isEmpty() || intersectionSize(dan, tuo) > 0) {
            return 0;
        }
        return combination(tuo.size(), k - dan.size());
    }

    private long distinctCartesian(String codes, int segmentCount, int min, int max) {
        List<Set<String>> box = segmentNumberSets(codes, segmentCount, min, max);
        if (box.size() != segmentCount) {
            return 0;
        }
        return distinctCartesian(box, 0, new HashSet<>());
    }

    private long distinctCartesian(List<Set<String>> box, int index, Set<String> used) {
        if (index == box.size()) {
            return 1;
        }
        long count = 0;
        for (String item : box.get(index)) {
            if (used.contains(item)) {
                continue;
            }
            used.add(item);
            count += distinctCartesian(box, index + 1, used);
            used.remove(item);
        }
        return count;
    }

    private long countInputGroups(String codes, int len, boolean unique, int min, int max, String rule) {
        long count = 0;
        for (String group : codeGroups(codes)) {
            String value = group.replace("&", "");
            if (!value.matches("\\d{" + len + "}")) {
                return 0;
            }
            List<String> chars = new ArrayList<>();
            for (int i = 0; i < value.length(); i++) {
                String ch = String.valueOf(value.charAt(i));
                if (!isIntInRange(ch, min, max)) {
                    return 0;
                }
                chars.add(ch);
            }
            if ("allUnique".equals(rule) && new HashSet<>(chars).size() != chars.size()) {
                return 0;
            }
            if ("onePair".equals(rule) && !hasExactlyOnePair(chars)) {
                return 0;
            }
            if (unique && new HashSet<>(chars).size() != chars.size()) {
                return 0;
            }
            count++;
        }
        return count;
    }

    private long countInputNumberGroups(String codes, int itemCount, int min, int max, boolean unique) {
        long count = 0;
        for (String group : codeGroups(codes)) {
            List<String> items = items(group);
            if (items.size() == 1 && items.get(0).length() == itemCount * 2) {
                items = splitFixedWidth(items.get(0), 2);
            }
            if (items.size() != itemCount) {
                return 0;
            }
            Set<String> set = new HashSet<>();
            for (String item : items) {
                if (!isIntInRange(item, min, max)) {
                    return 0;
                }
                set.add(normalNumber(item));
            }
            if (unique && set.size() != itemCount) {
                return 0;
            }
            count++;
        }
        return count;
    }

    private long sumSegmentsDigits(String codes, int expectedSegments) {
        return sumAllowedSegments(codes, expectedSegments, digitSet());
    }

    private long sumSegmentNumbers(String codes, int expectedSegments, int min, int max) {
        List<Set<String>> box = segmentNumberSets(codes, expectedSegments, min, max);
        return box.size() == expectedSegments ? sumSizes(box) : 0;
    }

    private long productAllowedSegments(String codes, int expectedSegments, Set<String> allowed) {
        List<Set<String>> box = segmentSets(codes, expectedSegments, allowed);
        return box.size() == expectedSegments ? productSizes(box) : 0;
    }

    private long sumAllowedSegments(String codes, int expectedSegments, Set<String> allowed) {
        List<Set<String>> box = segmentSets(codes, expectedSegments, allowed);
        return box.size() == expectedSegments ? sumSizes(box) : 0;
    }

    private long countAllowed(String codes, Set<String> allowed) {
        Set<String> values = new LinkedHashSet<>();
        for (String item : items(codes)) {
            String converted = convertSymbol(item);
            if (!allowed.contains(converted)) {
                return 0;
            }
            values.add(converted);
        }
        return values.size();
    }

    private long singleAllowed(String codes, Set<String> allowed) {
        return countAllowed(codes, allowed) == 1 ? 1 : 0;
    }

    private long singleNumber(String codes, int min, int max) {
        return countUniqueNumbers(codes, min, max) == 1 ? 1 : 0;
    }

    private boolean exactNumbers(String codes, int count, int min, int max) {
        Set<String> set = numbersSet(codes, min, max);
        return set.size() == count && items(codes).size() == count;
    }

    private int countUniqueDigits(String codes) {
        return (int) countAllowed(codes, digitSet());
    }

    private int countUniqueNumbers(String codes, int min, int max) {
        return numbersSet(codes, min, max).size();
    }

    private Set<String> numbersSet(String codes, int min, int max) {
        Set<String> set = new LinkedHashSet<>();
        for (String item : items(codes)) {
            if (!isIntInRange(item, min, max)) {
                return Collections.emptySet();
            }
            set.add(normalNumber(item));
        }
        return set;
    }

    private List<Set<String>> segmentSets(String codes, int expectedSegments, Set<String> allowed) {
        String[] raw = clean(codes).split("\\|", -1);
        List<Set<String>> result = new ArrayList<>();
        if (expectedSegments > 0 && raw.length != expectedSegments) {
            return result;
        }
        for (String segment : raw) {
            Set<String> set = new LinkedHashSet<>();
            if (StringUtils.hasText(segment)) {
                for (String item : items(segment)) {
                    String converted = convertSymbol(item);
                    if (!allowed.contains(converted)) {
                        return Collections.emptyList();
                    }
                    set.add(converted);
                }
            }
            result.add(set);
        }
        return result;
    }

    private List<Set<String>> segmentNumberSets(String codes, int expectedSegments, int min, int max) {
        String[] raw = clean(codes).split("\\|", -1);
        List<Set<String>> result = new ArrayList<>();
        if (expectedSegments > 0 && raw.length != expectedSegments) {
            return result;
        }
        for (String segment : raw) {
            Set<String> set = new LinkedHashSet<>();
            if (StringUtils.hasText(segment)) {
                for (String item : items(segment)) {
                    if (!isIntInRange(item, min, max)) {
                        return Collections.emptyList();
                    }
                    set.add(normalNumber(item));
                }
            }
            result.add(set);
        }
        return result;
    }

    private List<String> uniqueItems(String codes) {
        return new ArrayList<>(new LinkedHashSet<>(items(codes)));
    }

    private List<String> items(String codes) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(codes)) {
            return result;
        }
        for (String item : clean(codes).split("[&,]")) {
            if (StringUtils.hasText(item)) {
                result.add(convertSymbol(item.trim()));
            }
        }
        return result;
    }

    private List<String> codeGroups(String codes) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(codes)) {
            return result;
        }
        String normalized = clean(codes).replace('，', ',').replace(';', '|');
        String regex = normalized.contains("|") ? "\\|" : normalized.contains(",") ? "," : "\\s+";
        for (String item : normalized.split(regex)) {
            if (StringUtils.hasText(item)) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private List<String> splitFixedWidth(String value, int width) {
        List<String> result = new ArrayList<>();
        if (value.length() % width != 0) {
            return result;
        }
        for (int i = 0; i < value.length(); i += width) {
            result.add(value.substring(i, i + width));
        }
        return result;
    }

    private long combination(int n, int k) {
        if (n < 0 || k < 0 || n < k) {
            return 0;
        }
        if (n == k || k == 0) {
            return 1;
        }
        if (k == 1) {
            return n;
        }
        k = Math.min(k, n - k);
        long result = 1;
        for (int i = 1; i <= k; i++) {
            result = result * (n - k + i) / i;
        }
        return result;
    }

    private long productSizes(List<Set<String>> box) {
        long result = 1;
        for (Set<String> set : box) {
            if (set.isEmpty()) {
                return 0;
            }
            result *= set.size();
        }
        return result;
    }

    private long sumSizes(List<Set<String>> box) {
        long result = 0;
        for (Set<String> set : box) {
            result += set.size();
        }
        return result;
    }

    private int intersectionSize(Set<String> a, Set<String> b) {
        int count = 0;
        for (String item : a) {
            if (b.contains(item)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasExactlyOnePair(List<String> chars) {
        Map<String, Integer> counts = new HashMap<>();
        for (String ch : chars) {
            counts.put(ch, counts.getOrDefault(ch, 0) + 1);
        }
        return counts.containsValue(2) && counts.containsValue(1) && !counts.containsValue(3);
    }

    private long countK3AdjacentPair(List<String> items) {
        long count = 0;
        for (String item : items) {
            if (!item.matches("[1-6]{3}")) {
                return 0;
            }
            boolean pair = (item.charAt(0) == item.charAt(1) && item.charAt(1) != item.charAt(2))
                    || (item.charAt(1) == item.charAt(2) && item.charAt(0) != item.charAt(1));
            if (pair) {
                count++;
            }
        }
        return count;
    }

    private long countPairs(List<String> items, boolean same, int min, int max) {
        long count = 0;
        for (String item : items) {
            String value = item.replace("&", "");
            if (value.length() != 2) {
                return 0;
            }
            String a = value.substring(0, 1);
            String b = value.substring(1, 2);
            if (!isIntInRange(a, min, max) || !isIntInRange(b, min, max)) {
                return 0;
            }
            if ((same && a.equals(b)) || (!same && !a.equals(b))) {
                count++;
            }
        }
        return count;
    }

    private boolean cpDice(String codes) {
        List<String> values = items(codes);
        if (values.size() != 2 || !isIntInRange(values.get(0), 1, 6) || !isIntInRange(values.get(1), 1, 6)) {
            return false;
        }
        int a = Integer.parseInt(values.get(0));
        int b = Integer.parseInt(values.get(1));
        return a < b;
    }

    private boolean pk10Gyzh(String codes) {
        String[] parts = clean(codes).split("-");
        return parts.length == 2 && isIntInRange(parts[0], 1, 10) && isIntInRange(parts[1], 1, 10);
    }

    private long ftCode(String codes, String code) {
        String value = clean(codes);
        if ("FTF".equals(code)) {
            return value.matches("[1-4]番") ? 1 : 0;
        }
        if ("FTZ".equals(code)) {
            return value.matches("[1-4]正") ? 1 : 0;
        }
        if ("FTN".equals(code)) {
            if (!value.matches("[1-4]念[1-4]")) {
                return 0;
            }
            return value.charAt(0) != value.charAt(2) ? 1 : 0;
        }
        if ("FTJ".equals(code)) {
            if (!value.matches("[1-4][1-4]角")) {
                return 0;
            }
            int diff = Math.abs(value.charAt(0) - value.charAt(1));
            return diff == 1 || diff == 3 ? 1 : 0;
        }
        return 0;
    }

    private long vnPairGroups(LtProjectReq project, int itemCount) {
        long count = 0;
        for (String group : codeGroups(project.getCodes())) {
            List<String> values = items(group);
            if (values.size() != itemCount) {
                return 0;
            }
            Set<String> unique = new HashSet<>();
            for (String value : values) {
                if (!value.matches("\\d{2}")) {
                    return 0;
                }
                unique.add(value);
            }
            if (unique.size() != itemCount) {
                return 0;
            }
            count++;
        }
        return count;
    }

    private boolean mark6ExactOrDt(String codes, int k, int min, int max) {
        String[] parts = clean(codes).split("\\|", -1);
        if (parts.length == 1) {
            return exactNumbers(codes, k, min, max);
        }
        if (parts.length != 2) {
            return false;
        }
        Set<String> dan = numbersSet(parts[0], min, max);
        Set<String> tuo = numbersSet(parts[1], min, max);
        return dan.size() == k - 1 && tuo.size() == 1 && intersectionSize(dan, tuo) == 0;
    }

    private int digitstrCount(String digitstr) {
        if (!StringUtils.hasText(digitstr)) {
            return 0;
        }
        int count = 0;
        for (char ch : digitstr.toCharArray()) {
            if (Character.isDigit(ch)) {
                count++;
            }
        }
        return count;
    }

    private String convertSymbol(String token) {
        String value = normalize(token);
        Map<String, String> map = new HashMap<>();
        map.put("大", "0");
        map.put("小", "1");
        map.put("单", "2");
        map.put("雙", "3");
        map.put("双", "3");
        map.put("质", "4");
        map.put("合", "5");
        map.put("龙", "6");
        map.put("龍", "6");
        map.put("虎", "7");
        map.put("和", "8");
        map.put("没牛", "0");
        map.put("牛牛", "A");
        map.put("牛大", "B");
        map.put("牛小", "C");
        map.put("牛单", "D");
        map.put("牛双", "E");
        map.put("五条", "0");
        map.put("四条", "1");
        map.put("葫芦", "2");
        map.put("顺子", "3");
        map.put("三条", "4");
        map.put("两对", "5");
        map.put("一对", "6");
        map.put("散号", "7");
        map.put("总大", "0");
        map.put("总小", "1");
        map.put("总单", "2");
        map.put("总双", "3");
        map.put("总尾大", "4");
        map.put("总尾小", "5");
        map.put("上", "6");
        map.put("下", "8");
        map.put("奇", "0");
        map.put("偶", "2");
        for (int i = 1; i <= 9; i++) {
            map.put("牛" + i, String.valueOf(i));
        }
        return map.getOrDefault(value, value);
    }

    private Set<String> digitSet() {
        return rangeSet(0, 9);
    }

    private Set<String> rangeSet(int min, int max) {
        Set<String> result = new HashSet<>();
        for (int i = min; i <= max; i++) {
            result.add(String.valueOf(i));
            if (i > 0 && i < 10) {
                result.add("0" + i);
            }
        }
        return result;
    }

    private boolean isIntInRange(String value, int min, int max) {
        if (!StringUtils.hasText(value) || !value.matches("\\d+")) {
            return false;
        }
        int intValue = Integer.parseInt(value);
        return intValue >= min && intValue <= max;
    }

    private String normalNumber(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return String.valueOf(Integer.parseInt(value));
    }

    private Integer parseTrailingInt(String value, int defaultValue) {
        String digits = value.replaceAll("\\D+", "");
        return StringUtils.hasText(digits) ? Integer.parseInt(digits) : defaultValue;
    }

    private int numberBetween(String value, String prefix, String suffix) {
        String body = value.substring(prefix.length(), value.length() - suffix.length());
        return Integer.parseInt(body);
    }

    private BigDecimal modeRate(Integer mode) {
        if (mode == null) {
            return null;
        }
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
                return null;
        }
    }

    private boolean isSimpleAmountFunction(String functionType) {
        return SIMPLE_AMOUNT_FUNCTIONS.contains(functionType)
                || isVnFunction(functionType)
                || TH_FUNCTIONS.contains(functionType);
    }

    private boolean isVnFunction(String functionType) {
        return VN_FUNCTIONS.contains(functionType);
    }

    private boolean isInput(LtProjectReq project) {
        return "input".equalsIgnoreCase(project.getType());
    }

    private String functionType(BetContext context) {
        return context == null || context.getLottery() == null ? "" : normalize(context.getLottery().getFunctionType()).toUpperCase();
    }

    private String tryDecodePlainBase64(String codes) {
        try {
            byte[] decoded = Base64.getDecoder().decode(codes);
            String value = new String(decoded, StandardCharsets.UTF_8);
            return value.chars().allMatch(ch -> ch == '\n' || ch == '\r' || ch == '\t' || ch >= 32) ? value : codes;
        } catch (Exception e) {
            return codes;
        }
    }

    private String clean(String codes) {
        return normalize(codes).replace(" ", "").replace("，", ",");
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean in(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static Map<Integer, Integer> mapOf(Integer... values) {
        Map<Integer, Integer> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }

    private static Map<String, Integer> stringMapOf(Object... values) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), (Integer) values[i + 1]);
        }
        return result;
    }
}
