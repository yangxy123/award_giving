package com.giving.req;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class OrderFutureIssueReq {

    @NotBlank(message = "追号奖期不能为空")
    @ApiModelProperty(value = "追号奖期", required = true)
    private String issue;

    @NotNull(message = "追号倍数不能为空")
    @Min(value = 1, message = "追号倍数不能小于1")
    @ApiModelProperty(value = "追号倍数", required = true)
    private Long multiple;

    @NotNull(message = "追号勾选状态不能为空")
    @ApiModelProperty(value = "是否勾选", required = true)
    private Boolean checked;
}
