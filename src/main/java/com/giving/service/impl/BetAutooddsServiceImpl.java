package com.giving.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.giving.entity.AutooddsEntity;
import com.giving.entity.MethodEntity;
import com.giving.mapper.AutooddsMapper;
import com.giving.req.BetOrderReq;
import com.giving.req.LtProjectReq;
import com.giving.service.BetAutooddsService;
import com.giving.service.context.BetContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BetAutooddsServiceImpl implements BetAutooddsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Autowired
    private AutooddsMapper autooddsMapper;

    @Override
    public void validate(BetContext context, BetOrderReq req) {
        if (context == null || context.getLottery() == null
                || !Integer.valueOf(1).equals(context.getLottery().getIsAutoodds())
                || req == null || req.getLtProject() == null || req.getLtProject().isEmpty()) {
            return;
        }

        String functionType = normalize(context.getLottery().getFunctionType()).toUpperCase();
        boolean sinType = isSinType(functionType);
        String operator = context.getUser() == null ? "" : normalize(context.getUser().getOperator());
        String issue = context.getIssue() == null ? null : context.getIssue().getIssue();
        Date saleStart = context.getIssue() == null ? null : context.getIssue().getSaleStart();
        Map<Long, AutooddsAmount> amountByAoId = new LinkedHashMap<>();
        Map<Long, BigDecimal> existingByAoId = new LinkedHashMap<>();
        boolean oddsUpdated = false;

        for (LtProjectReq project : req.getLtProject()) {
            MethodEntity method = context.getMethodMap().get(project.getMethodId());
            if (method == null) {
                continue;
            }
            boolean k3SbYm = isK3SbYm(functionType, method);
            String codeName = resolveCodeName(project, sinType);
            List<AutooddsEntity> autooddsList = autooddsMapper.selectByMethodAndCode(
                    context.getLottery().getLotteryId().intValue(),
                    project.getMethodId(),
                    sinType ? codeName : null,
                    k3SbYm && StringUtils.hasText(codeName)
            );
            if (autooddsList == null || autooddsList.isEmpty()) {
                continue;
            }

            for (AutooddsEntity autoodds : autooddsList) {
                if (autoodds.getAutooddsId() == null || (k3SbYm && !normalize(autoodds.getCodeName()).contains("单"))) {
                    continue;
                }
                AutooddsAmount amount = amountByAoId.computeIfAbsent(autoodds.getAutooddsId(),
                        id -> new AutooddsAmount(autoodds, issue));
                amount.money = amount.money.add(nvl(project.getMoney()));

                if (hasCountOdds(autoodds) && StringUtils.hasText(project.getHprize())) {
                    BigDecimal existing = existingByAoId.computeIfAbsent(autoodds.getAutooddsId(),
                            id -> nvl(autooddsMapper.selectIssueTotalPrice(context.getTitle(), id, issue, operator)));
                    if (isHprizeOutdated(project.getHprize(), autoodds, existing, saleStart)) {
                        oddsUpdated = true;
                    }
                }
            }
        }

        boolean stop = false;
        boolean over = false;
        for (AutooddsAmount amount : amountByAoId.values()) {
            AutooddsEntity autoodds = amount.autoodds;
            BigDecimal existing = existingByAoId.computeIfAbsent(autoodds.getAutooddsId(),
                    id -> nvl(autooddsMapper.selectIssueTotalPrice(context.getTitle(), id, amount.issue, operator)));
            if (Integer.valueOf(1).equals(autoodds.getStopbet())) {
                stop = true;
            }
            BigDecimal blockade = nvl(autoodds.getBlockadevalue());
            if (blockade.compareTo(ZERO) > 0) {
                if (existing.compareTo(blockade) >= 0) {
                    stop = true;
                } else if (existing.add(amount.money).compareTo(blockade.add(nvl(autoodds.getBroadenvalue()))) > 0) {
                    over = true;
                }
            }
        }

        if (stop && over) {
            throw new IllegalStateException("玩法已停售且超过投注限额");
        }
        if (stop) {
            throw new IllegalStateException("玩法已停售");
        }
        if (over) {
            throw new IllegalStateException("超过投注限额");
        }
        if (oddsUpdated) {
            throw new IllegalStateException("赔率已更新");
        }
    }

    private boolean isHprizeOutdated(String hprize, AutooddsEntity autoodds, BigDecimal existingTotal, Date saleStart) {
        BigDecimal reduce = calculateAoReduce(autoodds, existingTotal, saleStart);
        if (reduce.compareTo(ZERO) <= 0) {
            return false;
        }
        BigDecimal baseOdds = firstPositive(autoodds.getDefaultodds(), autoodds.getHighestodds());
        if (baseOdds == null) {
            return false;
        }
        BigDecimal lowest = nvl(autoodds.getLowestodds());
        BigDecimal expected = baseOdds.subtract(reduce);
        if (lowest.compareTo(ZERO) > 0 && expected.compareTo(lowest) < 0) {
            expected = lowest;
        }
        BigDecimal normalizedExpected = expected.setScale(3, RoundingMode.HALF_UP);
        for (String item : hprize.split("[,/]")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            try {
                BigDecimal actual = new BigDecimal(item.trim()).setScale(3, RoundingMode.HALF_UP);
                if (actual.compareTo(normalizedExpected) == 0) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        return true;
    }

    private BigDecimal calculateAoReduce(AutooddsEntity autoodds, BigDecimal existingTotal, Date saleStart) {
        return calculateStageReduce(autoodds, existingTotal).add(calculateTraysReduce(autoodds, saleStart));
    }

    private BigDecimal calculateStageReduce(AutooddsEntity autoodds, BigDecimal existingTotal) {
        if (!Integer.valueOf(1).equals(autoodds.getIsCountoddsStage())
                || Integer.valueOf(1).equals(autoodds.getIsDeleteoddsStage())
                || !StringUtils.hasText(autoodds.getStage())) {
            return ZERO;
        }
        try {
            JSONArray stages = JSON.parseArray(autoodds.getStage());
            BigDecimal totalReduce = ZERO;
            for (Object item : stages) {
                if (!(item instanceof JSONObject)) {
                    continue;
                }
                JSONObject stage = (JSONObject) item;
                BigDecimal min = nvl(stage.getBigDecimal("min"));
                BigDecimal max = nvl(stage.getBigDecimal("max"));
                BigDecimal sum = nvl(stage.getBigDecimal("sum"));
                BigDecimal reduce = nvl(stage.getBigDecimal("reduce"));
                if (sum.compareTo(ZERO) <= 0 || reduce.compareTo(ZERO) <= 0) {
                    continue;
                }
                BigDecimal price = ZERO;
                if (existingTotal.compareTo(max) > 0) {
                    price = max.subtract(min);
                } else if (existingTotal.compareTo(max) <= 0 && existingTotal.compareTo(min) > 0) {
                    price = existingTotal.subtract(min);
                }
                if (price.compareTo(ZERO) > 0) {
                    totalReduce = totalReduce.add(price.divideToIntegralValue(sum).multiply(reduce));
                }
            }
            return totalReduce;
        } catch (Exception e) {
            throw new IllegalStateException("赔率配置错误");
        }
    }

    private BigDecimal calculateTraysReduce(AutooddsEntity autoodds, Date saleStart) {
        if (!Integer.valueOf(1).equals(autoodds.getIsCountoddsTrays())
                || Integer.valueOf(1).equals(autoodds.getIsDeleteoddsTrays())
                || !StringUtils.hasText(autoodds.getTrays())
                || autoodds.getAutooddsId() == null
                || saleStart == null) {
            return ZERO;
        }
        try {
            Integer codeCountValue = autooddsMapper.selectLatestCodeCount(autoodds.getAutooddsId(), saleStart);
            int codeCount = codeCountValue == null ? 0 : codeCountValue;
            String traysType = codeCount >= 0 ? "tray" : "stop";
            JSONArray traysData = JSON.parseArray(autoodds.getTrays());
            if (traysData == null || traysData.isEmpty() || !(traysData.get(0) instanceof JSONObject)) {
                return ZERO;
            }
            JSONArray rules = ((JSONObject) traysData.get(0)).getJSONArray(traysType);
            if (rules == null || rules.isEmpty()) {
                return ZERO;
            }
            BigDecimal reduce = ZERO;
            int count = Math.abs(codeCount);
            for (Object item : rules) {
                if (!(item instanceof JSONObject)) {
                    continue;
                }
                JSONObject rule = (JSONObject) item;
                Integer number = rule.getInteger("number");
                BigDecimal ruleReduce = nvl(rule.getBigDecimal("reduce"));
                if (number != null && count >= number && ruleReduce.compareTo(ZERO) > 0) {
                    reduce = ruleReduce;
                }
            }
            return reduce;
        } catch (Exception e) {
            throw new IllegalStateException("赔率配置错误");
        }
    }

    private boolean hasCountOdds(AutooddsEntity autoodds) {
        return (Integer.valueOf(1).equals(autoodds.getIsCountoddsStage())
                && !Integer.valueOf(1).equals(autoodds.getIsDeleteoddsStage()))
                || (Integer.valueOf(1).equals(autoodds.getIsCountoddsTrays())
                && !Integer.valueOf(1).equals(autoodds.getIsDeleteoddsTrays()));
    }

    private String resolveCodeName(LtProjectReq project, boolean sinType) {
        if (!sinType) {
            return null;
        }
        if (StringUtils.hasText(project.getUpperName())) {
            return normalize(project.getUpperName());
        }
        if (StringUtils.hasText(project.getScodeKey())) {
            return normalize(project.getScodeKey());
        }
        return normalize(project.getCodes());
    }

    private boolean isK3SbYm(String functionType, MethodEntity method) {
        String methodCode = normalize(method.getCode()).toUpperCase();
        return ("K3_SB".equals(functionType) && "YM".equals(methodCode))
                || ("K3_YXX".equals(functionType) && "DS".equals(methodCode));
    }

    private boolean isSinType(String functionType) {
        String value = normalize(functionType).toLowerCase();
        return value.contains("sin")
                || value.contains("lhd")
                || value.contains("sb")
                || value.contains("yxx")
                || value.contains("ft")
                || value.contains("bjl");
    }

    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        if (first != null && first.compareTo(ZERO) > 0) {
            return first;
        }
        if (second != null && second.compareTo(ZERO) > 0) {
            return second;
        }
        return null;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static class AutooddsAmount {
        private final AutooddsEntity autoodds;
        private final String issue;
        private BigDecimal money = ZERO;

        private AutooddsAmount(AutooddsEntity autoodds, String issue) {
            this.autoodds = autoodds;
            this.issue = issue;
        }
    }
}
