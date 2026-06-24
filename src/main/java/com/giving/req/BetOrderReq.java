package com.giving.req;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class BetOrderReq {

    /**
     * 对应 Laravel:
     * lotteryId => filled|int|exists:lottery,lottery_id
     *
     * lotteryId is optional; service resolves it from ltProject.methodId when absent.
     */
    @ApiModelProperty(value = "彩种ID")
    @JsonAlias({"lottery_id", "lotteryid"})
    private Integer lotteryId;

    @ApiModelProperty(value = "游戏类型")
    @JsonAlias("game_type")
    private String gameType;

    @ApiModelProperty(value = "游戏类型名称")
    @JsonAlias("game_type_name")
    private String gameTypeName;

    @NotBlank(message = "roomMasterId不能为空")
    @ApiModelProperty(value = "厅组ID", required = true)
    @JsonAlias({"room_master_id", "roommaster_id", "roommasterid"})
    private String roomMasterId;

    @NotBlank(message = "用户ID不能为空")
    @ApiModelProperty(value = "用户ID", required = true)
    @JsonAlias({"user_id", "userid"})
    private String userId;

    /**
     * 对应 Laravel:
     * ltProject => required|array
     * ltProject.* => required|array
     *
     * @Valid 很重要：
     * 没有它，List 里面 LtProjectReq 的字段校验不会生效。
     */
    @Valid
    @NotEmpty(message = "ltProject投注信息不能为空")
    @ApiModelProperty(value = "投注信息", required = true)
    @JsonAlias("lt_project")
    private List<LtProjectReq> ltProject;

    @NotBlank(message = "ltIssueStart不能为空")
    @ApiModelProperty(value = "投注奖期，普通投注传奖期号，快投可传now", required = true)
    @JsonAlias("lt_issue_start")
    private String ltIssueStart;

    @NotNull(message = "投注号码个数不能为空")
    @Min(value = 1, message = "投注号码个数不能小于1")
    @ApiModelProperty(value = "投注号码个数", required = true)
    @JsonAlias("lt_project_num")
    private Integer ltProjectNum;

    @NotNull(message = "投注金额数不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "投注金额数必须大于0")
    @ApiModelProperty(value = "投注金额数", required = true)
    @JsonAlias({"lt_money_amout", "lt_money_amount", "ltMoneyAmount"})
    private BigDecimal ltMoneyAmout;

    @ApiModelProperty(value = "时间戳")
    @JsonAlias({"timestamp", "time_stamp"})
    private Long timestemp;

    @NotNull(message = "orderFuture不能为空")
    @ApiModelProperty(value = "是否追号", required = true)
    @JsonAlias("order_future")
    private Boolean orderFuture;

    @ApiModelProperty(value = "是否追中即停")
    @JsonAlias("order_future_bingo_stop")
    private Boolean orderFutureBingoStop;

    @Valid
    @ApiModelProperty(value = "追号奖期计划")
    @JsonAlias("order_future_issue")
    private List<OrderFutureIssueReq> orderFutureIssue;

    @DecimalMin(value = "0", inclusive = false, message = "追号总金额必须大于0")
    @ApiModelProperty(value = "追号总金额")
    @JsonAlias("order_future_money_amount")
    private BigDecimal orderFutureMoneyAmount;

    @ApiModelProperty(value = "投注平台")
    private String platform;

    @ApiModelProperty(value = "显示语言")
    @JsonAlias("lang_show")
    private String langShow;

    @JsonAlias("app_fakebet")
    @ApiModelProperty(value = "是否虚拟投注，0否，1是")
    private Integer appFakebet;
}
