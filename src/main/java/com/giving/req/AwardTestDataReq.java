package com.giving.req;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.annotations.ApiModelProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class AwardTestDataReq {

    @NotNull(message = "厅组ID不能为空")
    @ApiModelProperty(value = "厅组ID", required = true)
    @JsonAlias({"room_master_id", "roommaster_id", "roommasterid"})
    private Integer roomMasterId;

    @NotNull(message = "彩种ID不能为空")
    @ApiModelProperty(value = "彩种ID", required = true)
    @JsonAlias({"lottery_id", "lotteryid"})
    private Long lotteryId;

    @NotBlank(message = "奖期不能为空")
    @ApiModelProperty(value = "奖期", required = true)
    private String issue;

    public Integer getRoomMasterId() {
        return roomMasterId;
    }

    public void setRoomMasterId(Integer roomMasterId) {
        this.roomMasterId = roomMasterId;
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
}
