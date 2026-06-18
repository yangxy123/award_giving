package com.giving.req;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class LtProjectReq {

    @ApiModelProperty(value = "彩种ID")
    private Integer lotteryId;

    @NotNull(message = "玩法ID不能为空")
    @ApiModelProperty(value = "玩法ID", required = true)
    private Integer methodId;

    @ApiModelProperty(value = "投注号码code")
    private String codes;

    @JsonAlias("scode_key")
    @ApiModelProperty(value = "投注号key")
    private String scodeKey;

    @NotNull(message = "onePrice不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "onePrice必须大于0")
    @ApiModelProperty(value = "单价", required = true)
    private BigDecimal onePrice;

    @NotNull(message = "times不能为空")
    @Min(value = 1, message = "times不能小于1")
    @ApiModelProperty(value = "倍数", required = true)
    private Long times;

    @ApiModelProperty(value = "奖期号")
    private String issue;

    @NotNull(message = "mode不能为空")
    @ApiModelProperty(value = "模式", required = true)
    private Integer mode;

    @ApiModelProperty(value = "投注方式")
    private String type;

    @ApiModelProperty(value = "号码位置信息")
    private String digitstr;

    @NotNull(message = "nums不能为空")
    @Min(value = 1, message = "nums不能小于1")
    @ApiModelProperty(value = "注数", required = true)
    private Integer nums;

    @NotNull(message = "money不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "money必须大于0")
    @ApiModelProperty(value = "金额", required = true)
    private BigDecimal money;

    @ApiModelProperty(value = "号码类型")
    private String codeType;

    @ApiModelProperty(value = "选择类型")
    private String selectType;

    @ApiModelProperty(value = "保留返点")
    private BigDecimal keepPoint;

    @ApiModelProperty(value = "前端传入奖金")
    private String hprize;

    @ApiModelProperty(value = "控赔或特殊玩法使用的上层名称")
    private String upperName;

    @ApiModelProperty(value = "奖金模式")
    private Integer omodel;
}
