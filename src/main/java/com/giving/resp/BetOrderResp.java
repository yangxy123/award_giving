package com.giving.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BetOrderResp {

    @ApiModelProperty(value = "彩种ID")
    private Integer lotteryId;

    @ApiModelProperty(value = "投注后可用余额")
    private BigDecimal availableBalance;

    @ApiModelProperty(value = "当前奖期")
    private String currentIssue;

    @ApiModelProperty(value = "注单ID列表")
    private List<String> projectId;

    @ApiModelProperty(value = "追号任务ID列表")
    private List<String> taskId;

    @ApiModelProperty(value = "投注总金额")
    private BigDecimal money;

    @ApiModelProperty(value = "兼容PHP的成功提示附加错误信息")
    private String errmsg;

    @ApiModelProperty(value = "追号停押期数")
    private Integer taskStopBet;

    @ApiModelProperty(value = "追号停押金额")
    private BigDecimal taskStopBetMoney;
}
