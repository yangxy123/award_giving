package com.giving.row;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动奖期生成过程中的临时数据行。
 *
 * <p>IssueRow 不直接对应数据库表，而是承载计算后的期号、销售时间、开奖时间、
 * 归属日期和休市状态，最终转换为 IssueInfoEntity 入库。</p>
 */
@Data
public class IssueRow {
    private final Integer lotteryId;
    private final String issue;
    private final LocalDate belongDate;
    private final LocalDate autoissueBelongDate;
    private final LocalDateTime saleStart;
    private final LocalDateTime saleEnd;
    private final LocalDateTime openDate;
    private final LocalDateTime cancelDeadline;
    private final Integer isClose;
}
