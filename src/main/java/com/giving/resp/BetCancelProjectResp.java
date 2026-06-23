package com.giving.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 撤单响应
 */
@Data
public class BetCancelProjectResp {

    @ApiModelProperty(value = "注单ID")
    private String projectId;

    @ApiModelProperty(value = "撤单账变ID")
    private String cancelOrderId;

    @ApiModelProperty(value = "撤单账变类型")
    private Integer cancelOrderTypeId;

    @ApiModelProperty(value = "撤单金额")
    private BigDecimal amount;

    @ApiModelProperty(value = "撤单后可用余额")
    private BigDecimal availableBalance;
}
