package com.giving.req;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/** 
* @author yangxy
* @version 创建时间：2025年12月30日 下午5:06:00 
*/
@Data
public class NoticeReq {
	@NotNull(message = "彩种ID不能为空")
	@ApiModelProperty(value = "彩种ID",required = true)
	private Integer lotteryId;
	
	@NotBlank(message = "奖期期号不能为空")
	@ApiModelProperty(value = "奖期期号",required = true)
	private String issue;

	@NotBlank(message = "号码不能为空")
	@ApiModelProperty(value = "号码",required = true)
	private String code;
}
