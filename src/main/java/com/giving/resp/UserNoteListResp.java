package com.giving.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午4:09
 */
@Data
public class UserNoteListResp {
    @ApiModelProperty(value = "奖期ID")
    private Integer issueId;

    @ApiModelProperty(value = "彩种ID")
    private Long lotteryId;

    @ApiModelProperty(value = "开奖号码")
    private String code;

    @ApiModelProperty(value = "奖期期号")
    private String issue;

    @ApiModelProperty(value = "开奖时间")
    private String openDate;

    @ApiModelProperty(value = "本期销售开始时间")
    private String saleStart;

    @ApiModelProperty(value = "本期销售截至时间")
    private String saleEnd;
}
