package com.giving.req;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:09
 */
@Data
public class ManualDistributionReq {
    @NotNull(message = "彩种ID不能为空")
    @ApiModelProperty(value = "彩种ID",required = true)
    private Long lotteryId;

    @NotBlank(message = "奖期期号不能为空")
    @ApiModelProperty(value = "奖期期号",required = true)
    private String issue;

    @NotBlank(message = "厅组id不能为空")
    @ApiModelProperty(value = "厅组id",required = true)
    private String masterId;
}
