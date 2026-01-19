package com.giving.controller;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

	@Autowired
	private AwardGivingService awardGivingService;
	
	@PostMapping("/notice")
	@ApiOperation("通知派奖")
	public void notice(@RequestBody @Valid NoticeReq noticeReq) {
		awardGivingService.notice(noticeReq);
	}

	@GetMapping("/createData/{count}")
	@ApiOperation("生成数据")
	public void createData(@PathVariable("count") Integer count) {
		awardGivingService.createData(count);
	}
}
