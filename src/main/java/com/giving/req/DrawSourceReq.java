package com.giving.req;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:50
 */
@Data
public class DrawSourceReq {
    @NotNull(message = "奖期期号")
    private String issue;

    @NotNull(message = "号码")
    private String winCode;

    @NotNull(message = "彩种ID不能为空")
    private Integer lotteryId;
}
