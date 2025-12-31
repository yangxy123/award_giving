package com.giving.controller;

import javax.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/** 
* @author yangxy
* @version 创建时间：2025年12月30日 下午5:02:50 
*/
@RestController
@RequestMapping("/awardgiving")
@Api(tags = "派奖相关")
public class AwardGivingController {
	private AwardGivingService awardGivingService;
	
	@PostMapping("/notice")
	@ApiOperation("通知派奖")
	public void notice(@RequestBody @Valid NoticeReq noticeReq) {
		
	}
}
