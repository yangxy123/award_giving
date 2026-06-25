package com.giving.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.req.CancelAwardReq;
import com.giving.resp.CancelAwardResp;
import com.giving.service.AwardGivingService;
import com.giving.service.CancelAwardService;
import com.giving.service.IssueInfoService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import javax.validation.Valid;

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

	@Autowired
	private IssueInfoService issueInfoService;

	@Autowired
	private CancelAwardService cancelAwardService;

	@GetMapping("/createData/{count}")
	@ApiOperation("生成数据")
	public ApiResp<String> createData(@PathVariable("count") Integer count) {
		return awardGivingService.createData(count);
	}

	@PostMapping("/cancelAward")
	@ApiOperation("撤销派奖")
	public ApiResp<CancelAwardResp> cancelAward(@RequestBody @Valid CancelAwardReq req) {
		return cancelAwardService.cancelAward(req);
	}
}
