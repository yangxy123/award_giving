package com.giving.base.resp;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午4:07
 */
@Data
public class PageResp<T> {
    @ApiModelProperty(value = "总条数")
    private Integer total;

    @ApiModelProperty(value = "显示条数")
    private Integer pageSize;

    @ApiModelProperty(value = "当前页码")
    private Integer pageNo;

    @ApiModelProperty(value = "分页结果集")
    private T data;
}