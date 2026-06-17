package com.giving.service.impl;

import com.giving.entity.IssueIdProducerEntity;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.LotteryMapper;
import com.giving.row.IssueRow;
import com.giving.service.AutoIssueService;
import com.giving.service.autoissue.IssueRule;
import com.giving.service.autoissue.IssueSet;
import com.giving.service.autoissue.VietnamTimeSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AutoIssueServiceImpl implements AutoIssueService {

    private static final ZoneId ZONE_ID = ZoneId.systemDefault();
    private static final DateTimeFormatter ISSUE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final LocalTime DEFAULT_CLOSE_START = LocalTime.of(5, 0, 0);
    private static final LocalTime DEFAULT_CLOSE_END = LocalTime.of(7, 0, 0);

    @Autowired
    private LotteryMapper lotteryMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;

    /**
     * 自动生成所有启用彩种的未来奖期。
     *
     * <p>方法会遍历启用彩种，根据 function_type 决定普通彩或越南彩的生成结束日期，
     * 再按彩种的 issueset、issue_rule、weekcycle 等配置展开奖期，最后过滤已过销售截止时间的奖期并写入 issue_info。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoIssue(Date startDate, Date endDate, Date vietnamEndDate, Integer createNum) {
        log.info("自动生成奖期开始，开始日期={}，普通彩结束日期={}，越南彩结束日期={}，生成期数={}",
                startDate, endDate, vietnamEndDate, createNum);

        List<LotteryEntity> lotteryList = lotteryMapper.selectLotterInAction();

        for (LotteryEntity lottery : lotteryList) {
            Date lotteryEndDate = endDate;

            switch (lottery.getFunctionType()) {
                case "MARK6_SIN":
                    // TODO: 若之后要支持六合彩，可用 createNum 计算结束日期。
                    lotteryEndDate = endDate;
                    break;
                case "VN_S":
                case "VN_C":
                case "VN_N":
                    lotteryEndDate = vietnamEndDate != null ? vietnamEndDate : endDate;
                    break;
                default:
                    lotteryEndDate = endDate;
                    break;
            }

            List<IssueSet> issueSets = parseIssueSet(lottery.getIssueset());
            List<IssueRow> issues;

            if (    (  Objects.equals(lottery.getFunctionType(), "VN_S")
                    || Objects.equals(lottery.getFunctionType(), "VN_C")
                    || Objects.equals(lottery.getFunctionType(), "VN_N"))
                    && issueSets.isEmpty()) {
                // 162~182 这类官方越南彩：每天符合 weekcycle 时只生成 YYYYMMDD-001。
                issues = generateVietnamOfficialIssues(lottery, startDate, lotteryEndDate);
            } else {
                // 212/223/256/10223/20223/243 这类快开：按 issueset + issue_rule 展开多期。
                issues = generateIssueSetIssues(lottery, issueSets, startDate, lotteryEndDate);
            }
            //issues解析生成奖期号
            issues = filterFutureSaleEnd(issues);

            if (issues.isEmpty()) {
                log.info("彩种 {} 未生成可销售奖期", lottery.getLotteryId());
                continue;
            }

            log.info("彩种 {} 生成奖期 {} 条，第一期={}，最后一期={}",
                    lottery.getLotteryId(),
                    issues.size(),
                    issues.get(0).getIssue(),
                    issues.get(issues.size() - 1).getIssue());
            //填入数据库
            persistIssues(lottery, issues);
        }
    }

    /**
     * 官彩
     * @param lottery
     * @param start
     * @param end
     * @return
     */
    /**
     * 生成官方越南彩奖期。
     *
     * <p>用于没有 issueset 的 VN_S、VN_C、VN_N 彩种；每个符合 weekcycle 的日期只生成一期，
     * 期号格式为 YYYYMMDD-001，并按固定开奖时间和销售截止时间计算销售区间。</p>
     */
    private List<IssueRow> generateVietnamOfficialIssues(LotteryEntity lottery, Date start, Date end) {
        List<IssueRow> rows = new ArrayList<>();

        LocalDate startDate = toLocalDate(start);
        LocalDate endDate = toLocalDate(end);
        VietnamTimeSetting setting = vietnamTimeSetting(lottery.getFunctionType());

        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            if (!inWeekCycle(date, lottery.getWeekcycle())) {
                continue;
            }

            LocalDateTime saleEnd = date.atTime(setting.getSaleEnd());
            LocalDateTime openDate = date.atTime(setting.getOpenDate());

            rows.add(new IssueRow(
                    Math.toIntExact(lottery.getLotteryId()),
                    date.format(ISSUE_DATE_FORMATTER) + "-001",
                    date,
                    date,
                    saleEnd.minusSeconds(setting.getStartEndTimeDiffSeconds()),
                    saleEnd,
                    openDate,
                    openDate.plusDays(1),
                    0
            ));
        }

        return rows;
    }

    /**短频彩
     * @param lottery
     * @param issueSets
     * @param start
     * @param end
     * @return
     */
    /**
     * 根据 issueset 生成快开类奖期。
     *
     * <p>issueset 定义每天的销售时间段、首期开奖时间、开奖周期、停售时间和撤单截止时间；
     * issue_rule 定义期号模板及跨日、跨月、跨年时序号是否重置。</p>
     */
    private List<IssueRow> generateIssueSetIssues( LotteryEntity lottery, List<IssueSet> issueSets, Date start, Date end ) {
        List<IssueRow> rows = new ArrayList<>();

        if (issueSets == null || issueSets.isEmpty()) {
            return rows;
        }

        IssueRule rule = IssueRule.parse(lottery.getIssueRule());
        LocalDate startDate = toLocalDate(start);
        LocalDate endDate = toLocalDate(end);

        int curIssueNumber = 1;
        LocalDate prevAutoIssueDate = null;

        issueSets.sort(Comparator.comparingInt(IssueSet::getSort));

        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            if (!inWeekCycle(date, lottery.getWeekcycle())) {
                continue;
            }

            for (IssueSet set : issueSets) {
                if (set.getStatus() == 0) {
                    continue;
                }

                long startSec = toSecond(set.getStarttime());
                long endSec = toSecond(set.getEndtime());
                long firstEndSec = toSecond(set.getFirstendtime());

                if (!set.isStarttimePositive()) {
                    startSec = -startSec;
                }

                if (endSec <= startSec) {
                    endSec += 86400;
                }

                long j = startSec;
                int count = 1;

                while (j <= endSec - set.getCycle()) {
                    long openSecond = (j == startSec && count == 1)
                            ? firstEndSec
                            : j + set.getCycle();

                    LocalDateTime rawSaleStart = date.atStartOfDay().plusSeconds(j);
                    LocalDateTime rawOpenDate = date.atStartOfDay().plusSeconds(openSecond);

                    LocalDateTime saleStart = rawSaleStart.minusSeconds(set.getEndsale());
                    LocalDateTime saleEnd = rawOpenDate.minusSeconds(set.getEndsale());
                    LocalDateTime openDate = rawOpenDate;
                    LocalDateTime cancelDeadline = rawOpenDate.plusSeconds(set.getDroptime());

                    if (prevAutoIssueDate != null) {
                        curIssueNumber = rule.nextSerialNumber(curIssueNumber, prevAutoIssueDate, date);
                    }

                    String issue = rule.generate(curIssueNumber, date);

                    rows.add(new IssueRow(
                            Math.toIntExact(lottery.getLotteryId()),
                            issue,
                            generateBelongDate(saleStart),
                            date,
                            saleStart,
                            saleEnd,
                            openDate,
                            cancelDeadline,
                            calcDefaultIsClose(date, saleStart, saleEnd)
                    ));

                    prevAutoIssueDate = date;

                    j = (j == startSec && count == 1) ? firstEndSec : j + set.getCycle();
                    count++;
                }
            }
        }

        return rows;
    }

    /**
     * 将生成好的奖期最终写入 issue_info。
     *
     * <p>写入前先查询同彩种同奖期是否已存在；对新奖期先写 issue_id_producer 获取全局唯一 issue_id，
     * 再批量插入 issue_info。整个调用处包在事务中，任一步失败都会回滚本次生成。</p>
     */
    private void persistIssues(LotteryEntity lottery, List<IssueRow> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        List<String> issueNos = issues.stream()
                .map(IssueRow::getIssue)
                .distinct()
                .collect(Collectors.toList());
        List<String> existed = issueInfoMapper.selectExistentIssues(lottery.getLotteryId(), issueNos);
        Set<String> existedSet = new HashSet<>(existed);
        Set<String> handledSet = new HashSet<>(existedSet);

        List<IssueInfoEntity> insertList = new ArrayList<>();
        Date now = new Date();
        for (IssueRow row : issues) {
            if (!handledSet.add(row.getIssue())) {
                continue;
            }

            IssueIdProducerEntity producer = new IssueIdProducerEntity();
            producer.setSuffix("0");
            issueInfoMapper.insertIssueIdProducer(producer);

            IssueInfoEntity issueInfo = new IssueInfoEntity();
            issueInfo.setIssueId(Math.toIntExact(producer.getIssueId()));
            issueInfo.setLotteryId(row.getLotteryId().longValue());
            issueInfo.setCode(null);
            issueInfo.setIssue(row.getIssue());
            issueInfo.setJobId(null);
            issueInfo.setOpenDate(toDate(row.getOpenDate()));
            issueInfo.setBelongDate(row.getBelongDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
            issueInfo.setAutoissueBelongDate(toDate(row.getAutoissueBelongDate()));
            issueInfo.setSaleStart(toDate(row.getSaleStart()));
            issueInfo.setSaleEnd(toDate(row.getSaleEnd()));
            issueInfo.setCancelDeadline(toDate(row.getCancelDeadline()));
            issueInfo.setWriteTime(null);
            issueInfo.setWriteId(null);
            issueInfo.setStatusFetch(0);
            issueInfo.setStatusCode(0);
            issueInfo.setCancelStatus(0);
            issueInfo.setCreatedAt(now);
            issueInfo.setUpdatedAt(now);
            issueInfo.setDrawType(0);
            issueInfo.setRandom(0);
            issueInfo.setIsClose(row.getIsClose());
            insertList.add(issueInfo);
        }

        if (insertList.isEmpty()) {
            log.info("lottery {} issue insert skipped, all {} issues already exist",
                    lottery.getLotteryId(), issues.size());
            return;
        }

        int insertCount = issueInfoMapper.batchInsertIssueInfo(insertList);
        if (insertCount != insertList.size()) {
            throw new IllegalStateException("issue insert count mismatch, lotteryId=" + lottery.getLotteryId()
                    + ", expected=" + insertList.size() + ", actual=" + insertCount);
        }

        log.info("lottery {} inserted {} issues, skipped {} existing issues",
                lottery.getLotteryId(), insertCount, issues.size() - insertList.size());
    }


    /**
     * 过滤销售截止时间已经过去的奖期，避免生成无法投注的历史奖期。
     */
    private List<IssueRow> filterFutureSaleEnd(List<IssueRow> issues) {
        LocalDateTime now = LocalDateTime.now(ZONE_ID);
        return issues.stream()
                .filter(issue -> issue.getSaleEnd().isAfter(now))
                .collect(Collectors.toList());
    }


    private boolean inWeekCycle(LocalDate date, Integer weekcycle) {
        if (weekcycle == null) {
            return false;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        int bit = 1 << (dayOfWeek.getValue() - 1);
        return (bit & weekcycle) > 0;
    }

    private VietnamTimeSetting vietnamTimeSetting(String functionType) {
        if (Objects.equals(functionType, "VN_C")) {
            return new VietnamTimeSetting(LocalTime.of(18, 10, 0), LocalTime.of(18, 15, 0), 604800);
        }

        if (Objects.equals(functionType, "VN_N")) {
            return new VietnamTimeSetting(LocalTime.of(19, 13, 0), LocalTime.of(19, 15, 0), 604800);
        }

        return new VietnamTimeSetting(LocalTime.of(17, 10, 0), LocalTime.of(17, 15, 0), 604800);
    }

    private LocalDate generateBelongDate(LocalDateTime saleStart) {
        // PHP 里用 env START_HOUR_FOR_A_DAY，默认通常是 0。
        int startHourForADay = 0;
        if (saleStart.getHour() < startHourForADay) {
            return saleStart.toLocalDate().minusDays(1);
        }
        return saleStart.toLocalDate();
    }

    private int calcDefaultIsClose(LocalDate date, LocalDateTime saleStart, LocalDateTime saleEnd) {
        LocalDateTime closeStart = date.atTime(DEFAULT_CLOSE_START);
        LocalDateTime closeEnd = date.atTime(DEFAULT_CLOSE_END);

        boolean insideCloseInterval =
                !saleStart.isBefore(closeStart)
                        && !saleEnd.isAfter(closeEnd);

        return insideCloseInterval ? 1 : 0;
    }

    private LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZONE_ID).toLocalDate();
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZONE_ID).toInstant());
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZONE_ID).toInstant());
    }

    private long toSecond(String time) {
        String[] parts = time.split(":");
        long hour = Long.parseLong(parts[0]);
        long minute = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
        long second = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
        return hour * 3600 + minute * 60 + second;
    }

    /**
     * 解析数据库中 PHP serialize 格式的 issueset 配置。
     *
     * <p>解析结果包含每天的开始时间、首期开奖时间、结束时间、周期、停售时间、撤单截止时间等，
     * 后续用于展开快开类彩种的多期奖期。</p>
     */
    private List<IssueSet> parseIssueSet(String serialized) {
        List<IssueSet> result = new ArrayList<>();

        if (serialized == null || serialized.trim().isEmpty()) {
            return result;
        }

        String text = serialized.trim().replace("\\\"", "\"");

        if ("a:0:{}".equals(text)) {
            return result;
        }

        Pattern blockPattern = Pattern.compile("i:\\d+;a:\\d+:\\{([^{}]*)\\}");
        Matcher matcher = blockPattern.matcher(text);

        while (matcher.find()) {
            String block = matcher.group(1);

            IssueSet set = new IssueSet();
            set.setStarttime(readPhpString(block, "starttime"));
            set.setFirstendtime(readPhpString(block, "firstendtime"));
            set.setEndtime(readPhpString(block, "endtime"));
            set.setCycle(readPhpInt(block, "cycle", 0));
            set.setEndsale(readPhpInt(block, "endsale", 0));
            set.setInputcodetime(readPhpInt(block, "inputcodetime", 0));
            set.setDroptime(readPhpInt(block, "droptime", 86400));
            set.setStatus(readPhpInt(block, "status", 1));
            set.setSort(readPhpInt(block, "sort", 0));
            set.setStarttimePositive(readPhpBoolean(block, "starttime_positive", true));

            if (set.getStarttime() != null && set.getFirstendtime() != null && set.getEndtime() != null) {
                result.add(set);
            }
        }

        return result;
    }

    private String readPhpString(String block, String key) {
        Pattern pattern = Pattern.compile("s:\\d+:\"" + Pattern.quote(key) + "\";s:\\d+:\"([^\"]*)\";");
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int readPhpInt(String block, String key, int defaultValue) {
        Pattern pattern = Pattern.compile("s:\\d+:\"" + Pattern.quote(key) + "\";i:(-?\\d+);");
        Matcher matcher = pattern.matcher(block);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    private boolean readPhpBoolean(String block, String key, boolean defaultValue) {
        Pattern boolPattern = Pattern.compile("s:\\d+:\"" + Pattern.quote(key) + "\";b:([01]);");
        Matcher boolMatcher = boolPattern.matcher(block);
        if (boolMatcher.find()) {
            return "1".equals(boolMatcher.group(1));
        }

        Pattern intPattern = Pattern.compile("s:\\d+:\"" + Pattern.quote(key) + "\";i:([01]);");
        Matcher intMatcher = intPattern.matcher(block);
        if (intMatcher.find()) {
            return "1".equals(intMatcher.group(1));
        }

        return defaultValue;
    }

}
