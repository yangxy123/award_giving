package com.giving.resp;

import io.swagger.annotations.ApiModelProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 撤销派奖响应
 */
public class CancelAwardResp {

    @ApiModelProperty(value = "厅组id")
    private String masterId;

    @ApiModelProperty(value = "厅主动态表前缀")
    private String title;

    @ApiModelProperty(value = "彩种ID")
    private Long lotteryId;

    @ApiModelProperty(value = "奖期期号")
    private String issue;

    @ApiModelProperty(value = "是否整期撤销")
    private Boolean cancelAll;

    @ApiModelProperty(value = "是否异步任务")
    private Boolean asyncStarted = false;

    @ApiModelProperty(value = "请求撤销的注单数量")
    private Integer targetProjectCount = 0;

    @ApiModelProperty(value = "实际撤销的注单数量")
    private Integer canceledProjectCount = 0;

    @ApiModelProperty(value = "跳过的注单数量")
    private Integer skippedProjectCount = 0;

    @ApiModelProperty(value = "撤销派奖总金额")
    private BigDecimal canceledAmount = BigDecimal.ZERO;

    @ApiModelProperty(value = "撤销结算总金额")
    private BigDecimal canceledDeductAmount = BigDecimal.ZERO;

    @ApiModelProperty(value = "是否重置厅主奖期验派状态")
    private Boolean issueInfoReset = false;

    @ApiModelProperty(value = "已撤销注单ID")
    private List<String> projectIds = new ArrayList<>();

    @ApiModelProperty(value = "撤销派奖账变ID")
    private List<String> cancelOrderIds = new ArrayList<>();

    @ApiModelProperty(value = "已删除的结算账变ID")
    private List<String> cancelDeductOrderIds = new ArrayList<>();

    public String getMasterId() {
        return masterId;
    }

    public void setMasterId(String masterId) {
        this.masterId = masterId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Boolean getCancelAll() {
        return cancelAll;
    }

    public void setCancelAll(Boolean cancelAll) {
        this.cancelAll = cancelAll;
    }

    public Boolean getAsyncStarted() {
        return asyncStarted;
    }

    public void setAsyncStarted(Boolean asyncStarted) {
        this.asyncStarted = asyncStarted;
    }

    public Integer getTargetProjectCount() {
        return targetProjectCount;
    }

    public void setTargetProjectCount(Integer targetProjectCount) {
        this.targetProjectCount = targetProjectCount;
    }

    public Integer getCanceledProjectCount() {
        return canceledProjectCount;
    }

    public void setCanceledProjectCount(Integer canceledProjectCount) {
        this.canceledProjectCount = canceledProjectCount;
    }

    public Integer getSkippedProjectCount() {
        return skippedProjectCount;
    }

    public void setSkippedProjectCount(Integer skippedProjectCount) {
        this.skippedProjectCount = skippedProjectCount;
    }

    public BigDecimal getCanceledAmount() {
        return canceledAmount;
    }

    public void setCanceledAmount(BigDecimal canceledAmount) {
        this.canceledAmount = canceledAmount;
    }

    public BigDecimal getCanceledDeductAmount() {
        return canceledDeductAmount;
    }

    public void setCanceledDeductAmount(BigDecimal canceledDeductAmount) {
        this.canceledDeductAmount = canceledDeductAmount;
    }

    public Boolean getIssueInfoReset() {
        return issueInfoReset;
    }

    public void setIssueInfoReset(Boolean issueInfoReset) {
        this.issueInfoReset = issueInfoReset;
    }

    public List<String> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<String> projectIds) {
        this.projectIds = projectIds;
    }

    public List<String> getCancelOrderIds() {
        return cancelOrderIds;
    }

    public void setCancelOrderIds(List<String> cancelOrderIds) {
        this.cancelOrderIds = cancelOrderIds;
    }

    public List<String> getCancelDeductOrderIds() {
        return cancelDeductOrderIds;
    }

    public void setCancelDeductOrderIds(List<String> cancelDeductOrderIds) {
        this.cancelDeductOrderIds = cancelDeductOrderIds;
    }
}
