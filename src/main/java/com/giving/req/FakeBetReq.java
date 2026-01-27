package com.giving.req;

import com.giving.base.req.BasePageReq;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class FakeBetReq extends BasePageReq {
    @NotNull(message = "彩种ID不能为空")
    @ApiModelProperty(value = "彩种ID",required = true)
    private Long lotteryId;

    @ApiModelProperty(value = "token")
    private String token;

    @NotBlank(message = "奖期期号不能为空")
    @ApiModelProperty(value = "奖期期号",required = true)
    private String issue;
}