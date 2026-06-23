package com.giving.req;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 撤单请求
 */
@Data
public class BetCancelProjectReq {

    @NotBlank(message = "roomMasterId不能为空")
    @ApiModelProperty(value = "厅组ID", required = true)
    private String roomMasterId;

    @NotBlank(message = "用户ID不能为空")
    @ApiModelProperty(value = "用户ID", required = true)
    private String userId;

    @NotBlank(message = "注单ID不能为空")
    @ApiModelProperty(value = "注单ID", required = true)
    private String projectId;

    @ApiModelProperty(value = "投注平台")
    private String platform;
}
