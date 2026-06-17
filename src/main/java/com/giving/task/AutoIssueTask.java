package com.giving.task;

import com.giving.management.DateSourceManagement;
import com.giving.service.AutoIssueService;
import com.giving.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * 自动奖期生成任务入口。
 *
 * <p>负责按照配置计算生成日期范围，并切换到 game_service 数据源执行奖期生成。
 * 任务本身只做调度、加锁和参数组装，实际奖期规则解析及入库由 {@link AutoIssueService} 完成。</p>
 */
@Slf4j
@Component
public class AutoIssueTask {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final String LOCK_KEY = "task:auto-issue:scan";
    private static final long LOCK_EXPIRE_SECONDS = 1800L;

    /** 普通彩种一次向后生成的天数。 */
    @Value("${issue.generator.command.days:1}")
    private Integer createDays;

    /** 越南彩种一次向后生成的天数。 */
    @Value("${issue.generator.command.vn-days:1}")
    private Integer createVnDays;

    /** 预留的按期数生成配置，六合彩等需要按期数控制时使用。 */
    @Value("${issue.generator.command.num:0}")
    private Integer createNum;

    @Autowired
    private AutoIssueService autoIssueService;

    @Autowired
    private RedisUtils redisUtils;

//    @Scheduled(cron = "${task.award.scan-cron:0 */1 * * * ?}")
    public void scanAutoIssue() {
        // 使用 Redis 锁防止多实例或手动触发时重复生成同一批奖期。
        String lockToken = UUID.randomUUID().toString();
        if (!redisUtils.setLock(LOCK_KEY, lockToken, LOCK_EXPIRE_SECONDS)) {
            log.info("自动奖期任务正在执行，本次跳过");
            return;
        }

        // 以 Asia/Shanghai 的自然日作为奖期生成起点。
        LocalDate startLocalDate = LocalDate.now(ZONE_ID);
        Date startDate = toDate(startLocalDate);
        Date endDate = toDate(startLocalDate.plusDays(createDays));
        Date vietnamEndDate = toDate(startLocalDate.plusDays(createVnDays));

        log.info("自动奖期任务开始，生成天数={}，越南彩生成天数={}，生成期数={}",
                createDays, createVnDays, createNum);
        try {
            // 奖期主表 issue_info 属于 game_service，生成过程需要切换到 gs 数据源。
            DateSourceManagement.use("gs");
            autoIssueService.autoIssue(startDate, endDate, vietnamEndDate, createNum);
        } finally {
            DateSourceManagement.clear();
            if (!redisUtils.unlock(LOCK_KEY, lockToken)) {
                log.warn("自动奖期任务锁未释放或已过期，锁键={}", LOCK_KEY);
            }
        }
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZONE_ID).toInstant());
    }
}
