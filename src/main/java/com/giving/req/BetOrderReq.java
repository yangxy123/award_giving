package com.giving.req;

import com.giving.resp.LtProjectResp;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class BetOrderReq {

    /**
     * 对应 Laravel:
     * lotteryId => filled|int|exists:lottery,lottery_id
     *
     * 注意：
     * 如果你想完全等同 filled，不是 required，
     * 这里可以不加 @NotNull。
     *
     * 但你原来 Java 代码里是必填，所以我先保留 @NotNull。
     */
    @NotNull(message = "彩种ID不能为空")
    @ApiModelProperty(value = "彩种ID", required = true)
    private Integer lotteryId;

    @NotNull(message = "roomMasterId不能为空")
    @ApiModelProperty(value = "厅组ID", required = true)
    private String roomMasterId;

    @NotNull(message = "用户ID不能为空")
    @ApiModelProperty(value = "用户ID", required = true)
    private String userId;

    /**
     * 对应 Laravel:
     * ltProject => required|array
     * ltProject.* => required|array
     *
     * @Valid 很重要：
     * 没有它，List 里面 LtProjectResp 的字段校验不会生效。
     */
    @Valid
    @NotEmpty(message = "ltProject投注信息不能为空")
    @ApiModelProperty(value = "投注信息", required = true)
    private List<LtProjectResp> ltProject;

    @NotNull(message = "ltIssueStart不能为空")
    @ApiModelProperty(value = "是否开始奖期", required = true)
    private Boolean ltIssueStart;

    @NotNull(message = "投注号码个数不能为空")
    @Min(value = 1, message = "投注号码个数不能小于1")
    @ApiModelProperty(value = "投注号码个数", required = true)
    private Integer ltProjectNum;

    @NotNull(message = "投注金额数不能为空")
    @Min(value = 1, message = "投注金额数不能小于1")
    @ApiModelProperty(value = "投注金额数", required = true)
    private Integer ltMoneyAmout;

    @NotNull(message = "时间戳不能为空")
    @ApiModelProperty(value = "时间戳", required = true)
    private Long timestemp;

    @NotNull(message = "orderFuture不能为空")
    @ApiModelProperty(value = "是否追号", required = true)
    private Boolean orderFuture;
}