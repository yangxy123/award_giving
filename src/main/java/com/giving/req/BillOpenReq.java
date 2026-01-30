package com.giving.req;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/30 下午4:04
 */
@Data
public class BillOpenReq {
    @ApiModelProperty(value = "奖期")
    String drawPeriod;

    @ApiModelProperty(value = "是否控号(1是，0否)")
    Integer isControl;

    @ApiModelProperty(value = "彩种类型(1:越南5分彩，2:越南30秒彩,3:泰国5分彩,4:亚洲30秒快三，5:亚洲1分快三,6:澳洲5分快三)")
    Integer lotteryType;
}
