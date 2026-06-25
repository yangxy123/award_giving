package com.giving.req;

import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 撤销派奖请求
 */
public class CancelAwardReq {

    @NotBlank(message = "厅组id不能为空")
    @ApiModelProperty(value = "厅组id", required = true)
    private String masterId;

    @NotNull(message = "彩种ID不能为空")
    @ApiModelProperty(value = "彩种ID", required = true)
    private Long lotteryId;

    @NotBlank(message = "奖期期号不能为空")
    @ApiModelProperty(value = "奖期期号", required = true)
    private String issue;

    @ApiModelProperty(value = "注单ID；单笔撤销时必填")
    private String projectId;

    @ApiModelProperty(value = "是否撤销整期全部已派奖订单；整期撤销时必须为true")
    private Boolean cancelAll;

    @ApiModelProperty(value = "投注平台")
    private String platform;

    public String getMasterId() {
        return masterId;
    }

    public void setMasterId(String masterId) {
        this.masterId = masterId;
    }

    public Long getLotteryId() {
        return lotteryId;
    }

    public void setLotteryId(Long lotteryId) {
        this.lotteryId = lotteryId;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Boolean getCancelAll() {
        return cancelAll;
    }

    public void setCancelAll(Boolean cancelAll) {
        this.cancelAll = cancelAll;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
