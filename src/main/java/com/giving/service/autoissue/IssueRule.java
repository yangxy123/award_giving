package com.giving.service.autoissue;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 奖期期号规则解析器。
 *
 * <p>负责解析 lottery.issue_rule，生成具体期号，并判断跨日、跨月、跨年时序号是否重置。</p>
 */
public class IssueRule {
    private final String template;
    private final boolean resetEveryYear;
    private final boolean resetEveryMonth;
    private final boolean resetEveryDay;
    private final int serialLength;

    private IssueRule(String template, boolean resetEveryYear, boolean resetEveryMonth, boolean resetEveryDay) {
        this.template = template;
        this.resetEveryYear = resetEveryYear;
        this.resetEveryMonth = resetEveryMonth;
        this.resetEveryDay = resetEveryDay;
        this.serialLength = parseSerialLength(template);
    }

    public static IssueRule parse(String code) {
        Matcher matcher = Pattern.compile("^([^|]*)\\|([01]),([01]),([01])").matcher(code);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid issue rule: " + code);
        }

        String template = matcher.group(1);
        boolean resetEveryYear = "0".equals(matcher.group(2));
        boolean resetEveryMonth = "0".equals(matcher.group(3));
        boolean resetEveryDay = "0".equals(matcher.group(4));

        return new IssueRule(template, resetEveryYear, resetEveryMonth, resetEveryDay);
    }

    public String generate(int serial, LocalDate date) {
        String issue = template;
        issue = issue.replace("Y", String.format("%04d", date.getYear()));
        issue = issue.replace("y", String.format("%02d", date.getYear() % 100));
        issue = issue.replace("m", String.format("%02d", date.getMonthValue()));
        issue = issue.replace("d", String.format("%02d", date.getDayOfMonth()));

        String serialCode = "[n" + serialLength + "]";
        issue = issue.replace(serialCode, String.format("%0" + serialLength + "d", serial));

        return issue;
    }

    public int nextSerialNumber(int previousSerialNumber, LocalDate prevDate, LocalDate nextDate) {
        if (shouldReset(prevDate, nextDate)) {
            return 1;
        }

        return previousSerialNumber + 1;
    }

    private boolean shouldReset(LocalDate prevDate, LocalDate nextDate) {
        if (resetEveryDay) {
            return !nextDate.isBefore(prevDate.plusDays(1));
        }

        if (resetEveryMonth) {
            LocalDate resetDate = prevDate.plusMonths(1).withDayOfMonth(1);
            return !nextDate.isBefore(resetDate);
        }

        if (resetEveryYear) {
            LocalDate resetDate = LocalDate.of(prevDate.getYear() + 1, 1, 1);
            return !nextDate.isBefore(resetDate);
        }

        return false;
    }

    private static int parseSerialLength(String template) {
        Matcher matcher = Pattern.compile("\\[n(\\d+)(?:,\\d+)?]").matcher(template);
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
