package com.giving.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class LtProjectResp {

    /**
     * 对应 Laravel:
     * ltProject.*.methodId => required_without:lotteryId|filled|int|exists:method,method_id
     *
     * 如果你现在 lotteryId 已经必填，那 methodId 建议也先设为必填。
     */
    @NotNull(message = "玩法ID不能为空")
    @ApiModelProperty(value = "玩法ID", required = true)
    private Integer methodId;

    @ApiModelProperty(value = "投注号码code")
    private String codes;

    @ApiModelProperty(value = "投注号key")
    private String scode_key;

    /**
     * 对应 Laravel:
     * ltProject.*.onePrice => required|numeric
     */
    @NotNull(message = "onePrice不能为空")
    @Min(value = 1, message = "onePrice不能小于1")
    @ApiModelProperty(value = "单价", required = true)
    private Integer onePrice;

    /**
     * 对应 Laravel:
     * ltProject.*.times => required|integer|min:1
     */
    @NotNull(message = "times不能为空")
    @Min(value = 1, message = "times不能小于1")
    @ApiModelProperty(value = "倍数", required = true)
    private Long times;

    @ApiModelProperty(value = "奖期号")
    private String issue;

    /**
     * 对应 Laravel:
     * ltProject.*.mode => required|integer
     */
    @NotNull(message = "mode不能为空")
    @ApiModelProperty(value = "模式", required = true)
    private Integer mode;

    @ApiModelProperty(value = "投注方式")
    private String type;

    @ApiModelProperty(value = "digitstr")
    private String digitstr;

    /**
     * 对应 Laravel:
     * ltProject.*.nums => required|integer|min:1
     */
    @NotNull(message = "nums不能为空")
    @Min(value = 1, message = "nums不能小于1")
    @ApiModelProperty(value = "号数", required = true)
    private Integer nums;

    /**
     * 对应 Laravel:
     * ltProject.*.money => required|numeric
     */
    @NotNull(message = "money不能为空")
    @Min(value = 1, message = "money不能小于1")
    @ApiModelProperty(value = "金额", required = true)
    private Integer money;
}