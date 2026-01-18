package com.giving.base.req;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午4:13
 */
@Data
public class BasePageReq {
    @NotNull(message = "thenumberofpagescannotbeempty")
    @ApiModelProperty(value = "页数", required = true)
    private Integer pageNo = 1;

    @NotNull(message = "thenumberofdisplayeditemsonthecurrentpagecannotbeempty")
    @ApiModelProperty(value = "当前页面显示条数", required = true)
    private Integer pageSize = 10;
}

