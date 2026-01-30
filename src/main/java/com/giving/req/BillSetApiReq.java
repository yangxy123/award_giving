package com.giving.req;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @author zzby
 * @version 创建时间： 2026/1/30 下午3:29
 */
@Data
public class BillSetApiReq {
    @NotNull(message = "url不能为空")
    @ApiModelProperty(value = "开票机设置盈利率api接口地址",required = true)
    String url;
}
