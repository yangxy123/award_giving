package com.giving.req;

import com.giving.base.req.BasePageReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午2:37
 */
@Data
public class UserNoteListReq extends BasePageReq {
    @NotNull(message = "彩种ID不能为空")
    @ApiModelProperty(value = "彩种ID",required = true)
    private Long lotteryId;

    @ApiModelProperty(value = "厅组id")
    private String masterId;

    @NotBlank(message = "奖期期号不能为空")
    @ApiModelProperty(value = "奖期期号",required = true)
    private String issue;
}
