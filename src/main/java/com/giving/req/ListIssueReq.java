package com.giving.req;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/15 下午4:14
 */
@Data
public class ListIssueReq {
    @NotNull(message = "奖期期号")
    private String issue;

    @NotNull(message = "彩种ID不能为空")
    private Integer lotteryId;
}
