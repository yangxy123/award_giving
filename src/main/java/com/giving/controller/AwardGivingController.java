package com.giving.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.service.AwardGivingService;
import com.giving.service.IssueInfoService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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

	@Autowired
	private IssueInfoService issueInfoService;

	@GetMapping("/createData/{count}")
	@ApiOperation("生成数据")
	public ApiResp<String> createData(@PathVariable("count") Integer count) {
		return awardGivingService.createData(count);
	}

	public static void main(String[] args) {
		List<BetInfoEntity> list = Lists.newArrayList();
		BetInfoEntity betInfoEntity = new BetInfoEntity();
		betInfoEntity.setCode("42,20");
		list.add(betInfoEntity);

		String code = "42,67,20,10,650,691,407,8154,5355,5712,2460,5262,6198,3708,0740,2780,6463,60434,65606,92114,46742,59900,03239,13583,59017,21114,90353";
		List<String> codeList = Lists.newArrayList(code.split(","));
		Map<Integer, List<String>> map = Maps.newConcurrentMap();
		map.put(7, Lists.newArrayList(getLast(codeList.get(0), 2), getLast(codeList.get(1), 2),
				getLast(codeList.get(2), 2), getLast(codeList.get(3), 2)));
		map.put(6, Lists.newArrayList(getLast(codeList.get(4), 2), getLast(codeList.get(5), 2),
				getLast(codeList.get(6), 2)));
		map.put(5, Lists.newArrayList(getLast(codeList.get(7), 2), getLast(codeList.get(8), 2),
				getLast(codeList.get(9), 2), getLast(codeList.get(10), 2), getLast(codeList.get(11), 2), getLast(codeList.get(12), 2)));
		map.put(4, Lists.newArrayList(getLast(codeList.get(13), 2), getLast(codeList.get(14), 2),
				getLast(codeList.get(15), 2), getLast(codeList.get(16), 2)));
		map.put(3,
				Lists.newArrayList(getLast(codeList.get(17), 2), getLast(codeList.get(18), 2),
						getLast(codeList.get(19), 2), getLast(codeList.get(20), 2), getLast(codeList.get(21), 2),
						getLast(codeList.get(22), 2)));
		map.put(2, Lists.newArrayList(getLast(codeList.get(23), 2), getLast(codeList.get(24), 2)));
		map.put(1, Lists.newArrayList(getLast(codeList.get(25), 2)));
		map.put(0, Lists.newArrayList(getLast(codeList.get(26), 2)));

		List<BetInfoEntity> collect = list.stream().filter(vo -> {
			String[] betCodes = vo.getCode().split(",");
			Integer firstNum = null;
			Integer twoNum = null;
			for (int n = 0; n < betCodes.length; n++) {
				String checkCode = betCodes[n].trim();
				for (int k = 7; k >= 0; k--) {
					if (firstNum != null && n == 1 && firstNum < k) {
						continue;
					}
					
					List<String> list2 = map.get(k);
					if (list2.contains(checkCode)) {
						if (n == 0) {
							firstNum = k;
							break;
						} else if (n == 1) {
							twoNum = k;
							break;
						}
					}
				}
				if (firstNum == null) {
					return false;
				} 
			}
			if (ObjectUtils.isEmpty(firstNum) || ObjectUtils.isEmpty(twoNum)) {
				return false;
			}

			if (firstNum > twoNum) {
				return true;
			}

			return false;
		}).collect(Collectors.toList());
		System.out.println(collect);
	}

	public static String getLast(String str, int len) {
		String trim = str.trim();
		return trim.substring(trim.length() - len);
	}
}
