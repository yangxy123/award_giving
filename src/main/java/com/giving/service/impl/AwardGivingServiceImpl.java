package com.giving.service.impl;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.giving.entity.OrdersEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.giving.entity.BetInfoEntity;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import springfox.documentation.spring.web.json.Json;

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
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private RoomMasterMapper roomMasterMapper;

	//	18z
	@Override
	public void notice(NoticeReq noticeReq) {
		int pageSize = 3000;
		int pageNo = 0;
		// 将开奖号码转换为list
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int maxSize = codeList.size() - 1;
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单

			List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
			if (list.isEmpty()) {
				break;
			}
//			Page<BetInfoEntity> page = (Page<BetInfoEntity>) list;
			pageNo += 1;

			//new Thread(()->{
			List<BetInfoEntity> allWinList = Lists.newArrayList();
			List<Integer> endList = Lists.newArrayList();

			new Thread(() -> {// 包组
				try {
					Map<String, Long> countMap = codeList.stream()
							.collect(Collectors.groupingBy(s -> s, Collectors.counting()));
					// 筛选出包组玩法的订单
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DBZ") ||
									vo.getMethodCode().equals("3DBZ") ||
									vo.getMethodCode().equals("4DBZ"))
							.collect(Collectors.toList());

					for (String key : countMap.keySet()) {
						Long multiple = countMap.get(key);

						if (key.length() == 2) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0
//											"2d包组玩法"
											|| vo.getCode().endsWith(key) && vo.getMethodCode().equals("2DBZ"))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
							});
							allWinList.addAll(winList);
						} else if (key.length() == 3) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0 || vo.getCode().endsWith(key)
											|| (vo.getCode()
											.indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
//											"2d包组玩法"
											&& vo.getMethodCode().equals("2DBZ"))
											|| (vo.getCode().endsWith(key.substring(key.length() - 2, key.length()))
//											"3d包组玩法"
											&& vo.getMethodCode().equals("3DBZ")))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> (vo.getCode()
											.indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
//											"2d包组玩法"
											&& vo.getMethodCode().equals("2DBZ"))
											|| (vo.getCode()
											.endsWith(key.substring(key.length() - 2, key.length()))
//											"2d包组玩法"
											&& vo.getMethodCode().equals("2DBZ"))
											|| (vo.getCode()
											.indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
//											"3d包组玩法"
											&& vo.getMethodCode().equals("3DBZ"))
											|| (vo.getCode().endsWith(key.substring(key.length() - 3, key.length()))
//											"3d包组玩法"
											&& vo.getMethodCode().equals("3DBZ"))
											|| vo.getCode()
											.indexOf(key.substring(key.length() - 4, key.length()) + ",") >= 0
											|| vo.getCode().endsWith(key.substring(key.length() - 4, key.length())))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DBZ7"))
							.collect(Collectors.toList());

					for (String key : group7_2d.keySet()) {
						Long multiple = group7_2d.get(key);

						if (key.length() == 2) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0 || vo.getCode().endsWith(key))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream().filter(
											vo -> vo.getCode().indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
													|| vo.getCode().endsWith(key.substring(key.length() - 2, key.length())))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DBZ7"))
							.collect(Collectors.toList());

					for (String key : group7_3d.keySet()) {
						Long multiple = group7_3d.get(key);

						if (key.length() == 3) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0 || vo.getCode().endsWith(key))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream().filter(
											vo -> vo.getCode().indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
													|| vo.getCode().endsWith(key.substring(key.length() - 3, key.length())))
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getWinbonus() * multiple);
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("PL2"))
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("PL3"))
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DT") ||
									vo.getMethodCode().equals("2DW") ||
									vo.getMethodCode().equals("2DTW"))
							.collect(Collectors.toList());
					String headCode = codeList.get(0);
					String endCode = codeList.get(17).substring(4, 6);
					List<BetInfoEntity> winList = betList.stream().filter(
									vo -> ((vo.getCode().indexOf(headCode + ",") >= 0 || vo.getCode().endsWith(headCode))
											//2d头玩法                  2d头尾玩法
											&& (vo.getMethodCode().equals("2DT") || vo.getMethodCode().equals("2DTW")))
											|| ((vo.getCode().indexOf(endCode + ",") >= 0 || vo.getCode().endsWith(endCode))
											//2d尾玩法 					2d头尾玩法
											&& (vo.getMethodCode().equals("2DW") || vo.getMethodCode().equals("2DTW"))))
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DT") ||
									vo.getMethodCode().equals("3DW") ||
									vo.getMethodCode().equals("3DTW"))
							.collect(Collectors.toList());
					String headCode = codeList.get(1);
					String endCode = codeList.get(17).substring(3, 6);
					List<BetInfoEntity> winList = betList.stream().filter(
									vo -> ((vo.getCode().indexOf(headCode + ",") >= 0 || vo.getCode().endsWith(headCode))
											//3d头玩法							3d头尾玩法
											&& (vo.getMethodCode().equals("3DT") || vo.getMethodCode().equals("3DTW")))
											|| ((vo.getCode().indexOf(endCode + ",") >= 0 || vo.getCode().endsWith(endCode))
											//3d尾玩法							3d头尾玩法
											&& (vo.getMethodCode().equals("3DW") || vo.getMethodCode().equals("3DTW"))))
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
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("4DW"))
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
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			//记录开始更改时间--state
			Date bonusTime = new Date();
			//中奖订单
			List<BetInfoEntity> sumList = getSumList(allWinList);
			//中奖订单ID列表
			List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
			//更新单注赢的钱
			betInfoMapper.updateWinbonus(noticeReq, sumList, bonusTime);
			//未中奖订单ID
			List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
					.collect(Collectors.toList());

			betInfoMapper.updateByNotWinList(noticeReq, notWinList);

			//中奖用户id列表
			List<String> userIds = sumList.stream().map(BetInfoEntity::getUserId).collect(Collectors.toSet()).stream().collect(Collectors.toList());

			//汇总累加用户奖金
			Map<String, BigDecimal> bonusMap = new HashMap<>();
			for (BetInfoEntity i : sumList) {
				if (bonusMap.containsKey(i.getUserId())) {
					bonusMap.put(i.getUserId(), bonusMap.get(i.getUserId()).add(new BigDecimal(i.getWinbonus())));
				} else {
					bonusMap.put(i.getUserId(), new BigDecimal(i.getWinbonus()));
				}
			}


			//锁定用户资金
			betInfoMapper.doLockUserFund(noticeReq, userIds);

			//账变写入 orders
//				betInfoMapper.addOrdersReArray(noticeReq,sumList);

			//删除临时注单记录
			List<String> projectIds = list.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
			projectsTmpMapper.deleteBatchIds(projectIds);

			//生成抄单（Speculation）记录（依业务类型）
//				roomMasterMapper.createSpeculation(noticeReq.getRoomMaster());

			//更新用户资金余额
			userFundMapper.updateUserFund(noticeReq, bonusMap);

			//解锁用户资金
			betInfoMapper.unLockUserFund(noticeReq, userIds);

			//}).start();
		}
//		List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
//		System.out.println(JSON.toJSONString(list));
	}

	public void createData() {
		List<String> uuidList = new ArrayList<>();
		for (int i = 1000; i<7000; i++){
			uuidList.add("3406965fcd2"+ i);
		}
		projectsTmpMapper.createData(uuidList);
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
		                             .mapToDouble(BetInfoEntity::getWinbonus)
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

	@Override
	public void noticeNorth(NoticeReq noticeReq) {
		// TODO Auto-generated method stub
		int pageSize = 3000;
		int pageNo = 0;
		// 将开奖号码转换为list
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int maxSize = codeList.size() - 1;
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
			List<BetInfoEntity> allWinList = Lists.newArrayList();
			List<Integer> endList = Lists.newArrayList();

			new Thread(()->{//2D头,尾，头尾玩法
				try {
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DT")
							|| vo.getMethodCode().equals("2DW")
							|| vo.getMethodCode().equals("2DTW"))
							.collect(Collectors.toList());
					List<String> headCodeList = codeList.stream().limit(4).collect(Collectors.toList());
					String endCode = codeList.get(maxSize).substring(3,5);
					for(String code:headCodeList) {
						List<BetInfoEntity> collect = betList.stream().filter(
								vo->vo.getCode().indexOf(code)>=0
//										"2D头尾"				"2D头"
										&& (vo.getMethodCode().equals("2DTW") || vo.getMethodCode().equals("2DT"))).collect(Collectors.toList());
						allWinList.addAll(collect);
					}
					List<BetInfoEntity> collect = betList.stream().filter(vo->vo.getCode().indexOf(endCode)>=0
//							"2D头尾"				"2D尾"
							&& (vo.getMethodCode().equals("2DTW") || vo.getMethodCode().equals("2DW"))).collect(Collectors.toList());
					allWinList.addAll(collect);
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
				endList.add(1);
			}).start();

			new Thread(()->{//3D头,尾，头尾玩法
				try {
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DT")
							|| vo.getMethodCode().equals("3DW")
							|| vo.getMethodCode().equals("3DTW"))
							.collect(Collectors.toList());
					List<String> headCodeList = codeList.stream().skip(4).limit(3).collect(Collectors.toList());
					String endCode = codeList.get(maxSize).substring(2,5);
					for(String code:headCodeList) {
						List<BetInfoEntity> collect = betList.stream().filter(vo->vo.getCode().indexOf(code)>=0
								//	"3D头尾"			"3D头"
								&& (vo.getMethodCode().equals("3DTW") || vo.getMethodCode().equals("3DT"))).collect(Collectors.toList());
						allWinList.addAll(collect);
					}
					List<BetInfoEntity> collect = betList.stream().filter(vo->vo.getCode().indexOf(endCode)>=0
								//"3D头尾" 			"3D尾"
							&& (vo.getMethodCode().equals("3DTW") || vo.getMethodCode().equals("3DW"))).collect(Collectors.toList());
					allWinList.addAll(collect);
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
				endList.add(2);
			}).start();

			new Thread(()->{//4D尾玩法
				try {
					//"4D尾"
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("4DW"))
							.collect(Collectors.toList());
					String endCode = codeList.get(maxSize).substring(1,5);
					List<BetInfoEntity> collect = betList.stream().filter(vo->vo.getCode().indexOf(endCode)>=0).collect(Collectors.toList());
					allWinList.addAll(collect);
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
				endList.add(3);
			}).start();

			new Thread(()->{//包组玩法
				try {
					Map<String, Long> countMap = codeList.stream()
							.collect(Collectors.groupingBy(s -> s, Collectors.counting()));
					//										"4D包组"
					List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("4DBZ")
							|| vo.getMethodCode().equals("3DBZ")
							|| vo.getMethodCode().equals("2DBZ"))
							.collect(Collectors.toList());
					for(String key:countMap.keySet()) {
						Long multiple = countMap.get(key);

						if (key.length() == 2) {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> vo.getCode().indexOf(key + ",") >= 0
											|| vo.getCode().endsWith(key) && vo.getMethodCode().equals("2DBZ") ) //"2d包组玩法"
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
													&& vo.getMethodCode().equals("2DBZ") )	//"2d包组玩法"
											|| (vo.getCode().endsWith(key.substring(key.length() - 2, key.length()))
													&& vo.getMethodCode().equals("3DBZ") ))	//"3d包组玩法"
									.collect(Collectors.toList());
							winList.forEach(vo -> {
								vo.setBonus(vo.getBonus() * multiple);
							});
							allWinList.addAll(winList);
						} else {
							List<BetInfoEntity> winList = betList.stream()
									.filter(vo -> (vo.getCode()
											.indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
											&& vo.getMethodCode().equals("2DBZ") )	//"2d包组玩法"
											|| (vo.getCode()
													.endsWith(key.substring(key.length() - 2, key.length()))
													&& vo.getMethodCode().equals("2DBZ") )//"2d包组玩法"
											|| (vo.getCode()
													.indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
													&& vo.getMethodCode().equals("3DBZ"))//"3d包组玩法"
											|| (vo.getCode().endsWith(key.substring(key.length() - 3, key.length()))
													&& vo.getMethodCode().equals("3DBZ") )//"3d包组玩法"
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
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
				endList.add(4);
			}).start();

			new Thread(()->{//pl2玩法
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
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
				endList.add(5);
			}).start();

			new Thread(()->{//pl3玩法
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
				}catch (Exception e) {
					// TODO: handle exception
					e.printStackTrace();
				}
				endList.add(5);
			}).start();

			//判断所有子线程是否执行完成
			while (true) {
				if (endList.size() == 5) {
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

	@Override
	public void noticeTh(NoticeReq noticeReq) {
		// TODO Auto-generated method stub
		int pageSize = 3000;
		int pageNo = 0;
		// 将开奖号码转换为list
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int maxSize = codeList.size() - 1;
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
			List<BetInfoEntity> allWinList = Lists.newArrayList();
			List<Integer> endList = Lists.newArrayList();

			new Thread(()->{//1D头、2D头、3D头、1D尾，2D尾
				try {
					List<BetInfoEntity> betList = list.stream().filter(vo->vo.getMethodId()==7).collect(Collectors.toList());
					String headCode = codeList.get(0).substring(3,6);
					String headCode1 = codeList.get(0).substring(4,6);
					String endCode = codeList.get(maxSize);

					List<BetInfoEntity> collect = betList.stream().filter(vo-> (headCode.indexOf(vo.getCode()) >= 0
							&& (vo.getMethodCode().equals("3DT") || vo.getMethodCode().equals("1DT") )) // "3D头"   "1D头"
							|| (headCode1.indexOf(vo.getCode()) >= 0 && vo.getMethodCode().equals("2DT"))//"2D头"
							|| (endCode.indexOf(vo.getCode()) && (vo.getMethodCode().equals("2DW") || vo.getMethodCode().equals("1DW"))))
//							|| (endCode.indexOf(vo.getCode()) && (vo.getMethodId() == "2D尾" || vo.getMethodId()=="1D尾")))
							.collect(Collectors.toList());
					allWinList.addAll(collect);
				}catch (Exception e) {
					// TODO: handle exception
				}
				endList.add(1);
			}).start();

			new Thread(()->{//3D前三、3D后三
				try {
					List<BetInfoEntity> betList = list.stream().filter(vo->vo.getMethodId()==7).collect(Collectors.toList());
					String frontThreeCode = codeList.get(1)+codeList.get(2);
					String afterThreeCode = codeList.get(3)+codeList.get(4);

					List<BetInfoEntity> collect = betList.stream().filter(vo-> ((frontThreeCode.startsWith(vo.getCode()) || frontThreeCode.endsWith(vo.getCode())) && vo.getMethodCode().equals("3DQ3")) //"3D前三"
							|| ((afterThreeCode.startsWith(vo.getCode()) || afterThreeCode.endsWith(vo.getCode())) &&  vo.getMethodCode().equals("3DH3"))).collect(Collectors.toList()); //"3D后三"
					allWinList.addAll(collect);
				}catch (Exception e) {
					// TODO: handle exception
				}
				endList.add(2);
			}).start();

			//判断所有子线程是否执行完成
			while (true) {
				if (endList.size() == 2) {
					break;
				}
				Thread.sleep(10);
			}
			//中奖订单 allWinList
			//中奖订单ID列表
			List<String> winIdList = allWinList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
			//未中奖订单ID
			List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
					.collect(Collectors.toList());
		}
	}
}
