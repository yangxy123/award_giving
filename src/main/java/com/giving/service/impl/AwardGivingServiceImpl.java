package com.giving.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.giving.entity.BetInfoEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yangxy
 * @version 创建时间：2025年12月30日 下午5:04:01
 */
@Slf4j
@Service
@Transactional
public class AwardGivingServiceImpl implements AwardGivingService {
	@Autowired
	private BetInfoMapper betInfoMapper;

	@Override
	public void notice(NoticeReq noticeReq) {
		int pageSize = 3000;
		int pageNo = 0;
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单
			Page<BetInfoEntity> page = (Page<BetInfoEntity>)betInfoMapper.selectList(
					new QueryWrapper<BetInfoEntity>().lambda().eq(BetInfoEntity::getIssue, noticeReq.getIssue())
							.eq(BetInfoEntity::getLotteryId, noticeReq.getLotteryId()).eq(BetInfoEntity::getIsCancel, 0)
							.eq(BetInfoEntity::getPrizeStatus, 0));
			List<BetInfoEntity> list = page.getResult();
			if(list.isEmpty()) {
				break;
			}
			pageNo +=1;
			// 将开奖号码转换为list
			List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
			int maxSize = codeList.size() - 1;
			List<BetInfoEntity> allWinList = Lists.newArrayList();
			List<Integer> endList = Lists.newArrayList();
			
			new Thread(() -> {// 包组
				try {
					Map<String, Long> countMap = codeList.stream()
							.collect(Collectors.groupingBy(s -> s, Collectors.counting()));
					// 筛选出包组玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());

					for (String key : countMap.keySet()) {
						Long multiple = countMap.get(key);

						if (key.length() == 2) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0
											|| vo.getCode().endsWith(key) && vo.getMethodId() == "2d包组玩法")
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						} else if (key.length() == 3) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0 || vo.getCode().endsWith(key)
											|| (vo.getCode()
													.indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
													&& vo.getMethodId() == "2d包组玩法")
											|| (vo.getCode().endsWith(key.substring(key.length() - 2, key.length()))
													&& vo.getMethodId() == "3d包组玩法"))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> (vo.getCode()
											.indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
											&& vo.getMethodId() == "2d包组玩法")
											|| (vo.getCode()
													.endsWith(key.substring(key.length() - 2, key.length()))
													&& vo.getMethodId() == "2d包组玩法")
											|| (vo.getCode()
													.indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
													&& vo.getMethodId() == "3d包组玩法")
											|| (vo.getCode().endsWith(key.substring(key.length() - 3, key.length()))
													&& vo.getMethodId() == "3d包组玩法")
											|| vo.getCode()
													.indexOf(key.substring(key.length() - 4, key.length()) + ",") >= 0
											|| vo.getCode().endsWith(key.substring(key.length() - 4, key.length())))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						}

					}
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(1);
			}).start();
			new Thread(() -> {// 2D包组7
				try {
					List<String> collect = codeList.stream().limit(6).collect(Collectors.toList());
					collect.add(codeList.get(maxSize));
					Map<String, Long> group7_2d = collect.stream()
							.collect(Collectors.groupingBy(s -> s, Collectors.counting()));
					// 筛选出2D包组7玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());

					for (String key : group7_2d.keySet()) {
						Long multiple = group7_2d.get(key);

						if (key.length() == 2) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0 || vo.getCode().endsWith(key))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream().filter(
									vo -> vo.getCode().indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
											|| vo.getCode().endsWith(key.substring(key.length() - 2, key.length())))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						}
					}
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(2);
			}).start();
			new Thread(() -> {// 3D包组7
				try {
					List<String> collect = codeList.stream().skip(1).limit(6).collect(Collectors.toList());
					collect.add(codeList.get(maxSize));
					Map<String, Long> group7_3d = collect.stream()
							.collect(Collectors.groupingBy(s -> s, Collectors.counting()));
					// 筛选出3D包组7玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());

					for (String key : group7_3d.keySet()) {
						Long multiple = group7_3d.get(key);

						if (key.length() == 3) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0 || vo.getCode().endsWith(key))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream().filter(
									vo -> vo.getCode().indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
											|| vo.getCode().endsWith(key.substring(key.length() - 3, key.length())))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						}
					}
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(3);
			}).start();
			new Thread(() -> {// pl2
				try {
					// 筛选出pl2玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());
					List<BetInfoEntity> winList = betList.stream()
							.filter(vo -> noticeReq.getCode().indexOf(vo.getCode().split(",")[0] + ",") >= 0
									&& (noticeReq.getCode().indexOf(vo.getCode().split(",")[0] + ",") < noticeReq
											.getCode().indexOf(vo.getCode().split(",")[1] + ",")
											|| noticeReq.getCode().endsWith(vo.getCode().split(",")[1])))
							.collect(Collectors.toList());
					allWinList.addAll(winList);
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(4);
			}).start();
			new Thread(() -> {// pl3
				try {
					// 筛选出pl2玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());
					List<BetInfoEntity> winList = betList.stream().filter(vo -> noticeReq.getCode()
							.indexOf(vo.getCode().split(",")[0] + ",") >= 0
							&& noticeReq.getCode().indexOf(vo.getCode().split(",")[1] + ",") >= 0
							&& (noticeReq.getCode().indexOf(vo.getCode().split(",")[0] + ",") < noticeReq.getCode()
									.indexOf(vo.getCode().split(",")[1] + ",")
									&& (noticeReq.getCode().indexOf(vo.getCode().split(",")[1] + ",") < noticeReq
											.getCode().indexOf(vo.getCode().split(",")[2] + ",")
											|| noticeReq.getCode().endsWith(vo.getCode().split(",")[2]))))
							.collect(Collectors.toList());
					allWinList.addAll(winList);
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(5);
			}).start();
			new Thread(() -> {//2d头、尾、头尾
				try {
					//筛选出2d头、尾、头尾玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());
					String headCode = codeList.get(0);
					String endCode = codeList.get(17).substring(4, 6);
					List<BetInfoEntity> winList = betList.stream().filter(
							vo -> ((vo.getCode().indexOf(headCode + ",") >= 0 || vo.getCode().endsWith(headCode))
									&& (vo.getMethodId() == "2d头玩法" || vo.getMethodId() == "2d头尾玩法"))
									|| ((vo.getCode().indexOf(endCode + ",") >= 0 || vo.getCode().endsWith(endCode))
											&& (vo.getMethodId() == "2d尾玩法" || vo.getMethodId() == "2d头尾玩法")))
							.collect(Collectors.toList());
					allWinList.addAll(winList);
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(6);
			}).start();
			new Thread(() -> {//3d头、尾、头尾
				try {
					//筛选出3d头、尾、头尾玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());
					String headCode = codeList.get(1);
					String endCode = codeList.get(17).substring(3, 6);
					List<BetInfoEntity> winList = betList.stream().filter(
							vo -> ((vo.getCode().indexOf(headCode + ",") >= 0 || vo.getCode().endsWith(headCode))
									&& (vo.getMethodId() == "3d头玩法" || vo.getMethodId() == "3d头尾玩法"))
									|| ((vo.getCode().indexOf(endCode + ",") >= 0 || vo.getCode().endsWith(endCode))
											&& (vo.getMethodId() == "3d尾玩法" || vo.getMethodId() == "3d头尾玩法")))
							.collect(Collectors.toList());
					allWinList.addAll(winList);
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(7);
			}).start();
			new Thread(() -> {//4D尾玩法
				try {
					//筛选出4d尾玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodId() == 7)
							.collect(Collectors.toList());
					String endCode = codeList.get(17).substring(3, 6);
					List<BetInfoEntity> winList = betList.stream()
							.filter(vo -> vo.getCode().indexOf(endCode + ",") >= 0 || vo.getCode().endsWith(endCode))
							.collect(Collectors.toList());
					allWinList.addAll(winList);
				} catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
					log.error("");
				}
				endList.add(8);
			}).start();
			//判断所有子线程是否执行完成
			while (true) {
				if (endList.size() == 8) {
					break;
				}
				Thread.sleep(10);
			}
			//中奖订单
			List<BetInfoEntity> sumList = getSumList(allWinList);
			//中奖订单ID列表
			List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
			//未中奖订单ID
			List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
					.collect(Collectors.toList());
			
			
		}
	}
	
	/**
	 * 相同投注订单计算中奖总金额
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午7:26:09 
	* @param allWinList
	* @return
	 */
	private List<BetInfoEntity> getSumList(List<BetInfoEntity> allWinList) {
		
		return allWinList.stream()
		         .collect(Collectors.collectingAndThen(
		             Collectors.groupingBy(
		            	 BetInfoEntity::getProjectId,
		                 Collectors.collectingAndThen(
		                     Collectors.toList(),
		                     group -> {
		                         // 计算总分
		                         Double totalScore = group.stream()
		                             .mapToDouble(BetInfoEntity::getBonus)
		                             .sum();
		                         
		                         // 获取第一条记录
		                         BetInfoEntity first = group.get(0);
		                         
		                         BetInfoEntity vo = new BetInfoEntity();
		                         BeanUtils.copyProperties(first, vo);
		                         vo.setBonus(totalScore);
		                         vo.setIsGetprize(1);
		                         // 创建汇总对象
		                         return vo;
		                     }
		                 )
		             ),
		             map -> new ArrayList<>(map.values())
		         ));
	}
}
