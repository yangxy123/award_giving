package com.giving.service.impl;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.*;
import com.giving.util.JdbcCreateSqlUtil;
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
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;

	@Autowired
	private JdbcCreateSqlUtil jdbcCreateSqlUtil;

	@Override
	public void notice(NoticeReq noticeReq) {
		Long startTime = System.currentTimeMillis();
		int pageSize = 3000;
		int pageNo = 1;
		Date bonusTime = new Date();
		// 将开奖号码转换为list
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int maxSize = codeList.size() - 1;
		List<List<BetInfoEntity>> allBetList = Lists.newArrayList();
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单
			List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
			if (list.isEmpty() || list == null) {
				//log.info("===========订单查询完毕 page:{}",pageNo);
				break;
			}
			allBetList.add(list);
			pageNo += 1;
		}
		int count = 0;
		List<Integer> countList = Lists.newArrayList();
		for(List<BetInfoEntity> list: allBetList){
			int i = count;
			new Thread(()->{
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
				//中奖订单
				List<BetInfoEntity> sumList = getSumList(allWinList);
				updateDataAll(sumList,noticeReq,list,bonusTime,"notice");
				countList.add(i);
			}).start();
			count++;
		}
		while (true){
//			System.out.println("\n============== " + countList.size() + " / " + allBetList.size());
//			for (Integer i : countList) {
//				System.out.print(i+" ");
//			}
			if(countList.size() == allBetList.size()){
				break;
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		Long endTime = System.currentTimeMillis();
		log.info("\n============== notice - {} ================" +
				"\nlotteryId = {}" +
				"\nissue = {}" +
				"\n注单数(3000):{}" +
				"\n开始时间:{}" +
				"\n结束时间:{}" +
				"\n耗时:{}",noticeReq.getTitle(),noticeReq.getLotteryId(),noticeReq.getIssue(),allBetList.size(),startTime,endTime,endTime - startTime);
	}

	@Override
	public void noticeNorth(NoticeReq noticeReq) {
		try {


		// TODO Auto-generated method stub
		int pageSize = 3000;
		int pageNo = 1;
		// 将开奖号码转换为list
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int maxSize = codeList.size() - 1;
		Date bonusTime = new Date();
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单
			List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
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
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
			//中奖订单
			List<BetInfoEntity> sumList = getSumList(allWinList);
			updateDataAll(sumList,noticeReq,list,bonusTime,"noticeNorth");

		}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@Override
	public void noticeTh(NoticeReq noticeReq) {
		// TODO Auto-generated method stub
		int pageSize = 3000;
		int pageNo = 1;
		// 将开奖号码转换为list
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int maxSize = codeList.size() - 1;
		Date bonusTime = new Date();
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单
			List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
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
							|| (endCode.indexOf(vo.getCode()) >=0 && (vo.getMethodCode().equals("2DW") || vo.getMethodCode().equals("1DW"))))
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
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
			//中奖订单
			List<BetInfoEntity> sumList = getSumList(allWinList);
//			//中奖订单ID列表
//			List<String> winIdList = allWinList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
//			//未中奖订单ID
//			List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
//					.collect(Collectors.toList());

			updateDataAll(sumList,noticeReq,list,bonusTime,"noticeTh");
		}
	}

	@Override
	public void noticeLw(NoticeReq noticeReq) {
		Date bonusTime = new Date();
		// TODO Auto-generated method stub
		int pageSize = 3000;
		int pageNo = 1;
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		String headCode = codeList.get(0);
		String endCode = codeList.get(1);
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单
			List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
			if(list.isEmpty()) {
				break;
			}
			pageNo +=1;
			// 将开奖号码转换为list
			List<BetInfoEntity> allWinList = Lists.newArrayList();
			//1D头、尾 2D头尾,3D头校验
			List<BetInfoEntity> collect = list.stream().filter(vo->(headCode.indexOf(vo.getCode()) >= 0 && ("1D头".equals(vo.getMethodCode()) || "3D头".equals(vo.getMethodCode())))
					|| (endCode.indexOf(vo.getCode()) >= 0 && ("1D尾".equals(vo.getMethodCode()) || "2D尾".equals(vo.getMethodCode())))
					|| (headCode.substring(1, 3).indexOf(vo.getCode()) >= 0 && "2D头".equals(vo.getMethodCode()))).collect(Collectors.toList());
			allWinList.addAll(collect);

			//3D组选
			List<BetInfoEntity> collect2 = list.stream().filter(vo->endCode.indexOf(vo.getCode().substring(0,1)+vo.getCode().substring(1,2)+vo.getCode().substring(2,3)) >= 0
						|| endCode.indexOf(vo.getCode().substring(0,1)+vo.getCode().substring(2,3)+vo.getCode().substring(1,2)) >= 0
						|| endCode.indexOf(vo.getCode().substring(1,2)+vo.getCode().substring(0,1)+vo.getCode().substring(2,3)) >= 0
						|| endCode.indexOf(vo.getCode().substring(1,2)+vo.getCode().substring(2,3)+vo.getCode().substring(0,1)) >= 0
						|| endCode.indexOf(vo.getCode().substring(2,3)+vo.getCode().substring(0,1)+vo.getCode().substring(1,2)) >= 0
						|| endCode.indexOf(vo.getCode().substring(2,3)+vo.getCode().substring(1,2)+vo.getCode().substring(0,1)) >= 0
					).collect(Collectors.toList());
			allWinList.addAll(collect2);

			//中奖订单 allWinList
			//中奖订单ID列表
//			List<String> winIdList = allWinList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
//			//未中奖订单ID
//			List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
//					.collect(Collectors.toList());

			List<BetInfoEntity> sumList = getSumList(allWinList);

			updateDataAll(sumList,noticeReq,list,bonusTime,"noticeLw");
		}
	}

	@Override
	public void noticeKs(NoticeReq noticeReq) {
		Date bonusTime = new Date();
		// TODO Auto-generated method stub
		int pageSize = 3000;
		int pageNo = 1;
		List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
		int totalNum = Integer.parseInt(codeList.get(0)) + Integer.parseInt(codeList.get(1)) + Integer.parseInt(codeList.get(2));
		while (true) {
			PageHelper.startPage(pageNo, pageSize);
			// TODO Auto-generated method stub
			// 获取对应奖期对应彩种未撤单且未派奖的所有订单
			List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
			if(list.isEmpty()) {
				break;
			}
			pageNo +=1;

			List<BetInfoEntity> allWinList = list.stream().filter(vo-> ((vo.getCode().indexOf(codeList.get(0)+codeList.get(1)+codeList.get(2)) >= 0
					|| vo.getCode().indexOf(codeList.get(0)+codeList.get(2)+codeList.get(1)) >= 0
					|| vo.getCode().indexOf(codeList.get(1)+codeList.get(0)+codeList.get(2)) >= 1
					|| vo.getCode().indexOf(codeList.get(1)+codeList.get(2)+codeList.get(0)) >= 0
					|| vo.getCode().indexOf(codeList.get(2)+codeList.get(0)+codeList.get(1)) >= 0
					|| vo.getCode().indexOf(codeList.get(2)+codeList.get(1)+codeList.get(0)) >= 0)
					&& ("三同号".equals(vo.getMethodCode()) || "三不号".equals(vo.getMethodCode()) ||"二同号".equals(vo.getMethodCode()) ||"二不同号".equals(vo.getMethodCode())))
					|| ((vo.getCode().indexOf(codeList.get(0)) >=0 || vo.getCode().indexOf(codeList.get(1)) >=0 || vo.getCode().indexOf(codeList.get(2)) >=0)
						 && "猜一个号".equals(vo.getMethodCode())	)
					|| (totalNum < 10 && vo.getCode().indexOf("0"+totalNum) >= 0 && "和值".equals(vo.getMethodCode()))
					|| (totalNum >= 10 && vo.getCode().indexOf(String.valueOf(totalNum)) >= 0 && "和值".equals(vo.getMethodCode()))).collect(Collectors.toList());
			//中奖订单 allWinList
			//中奖订单ID列表
//			List<String> winIdList = allWinList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
//			//未中奖订单ID
//			List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
//					.collect(Collectors.toList());

			List<BetInfoEntity> sumList = getSumList(allWinList);
			updateDataAll(sumList,noticeReq,list,bonusTime,"noticeLw");
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

	public void newUpdateAll(List<BetInfoEntity> sumList,NoticeReq noticeReq,List<BetInfoEntity> list,Date bonusTime){
		//中奖订单ID列表
		List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
		//未中奖订单ID
		List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
				.collect(Collectors.toList());
		if(!notWinList.isEmpty()){
			List<BetInfoEntity> tempList = new ArrayList<>();
			tempList.addAll(notWinList);
			tempList.forEach(vo -> {
				vo.setIsGetprize(2);
				vo.setPrizeStatus(1);
				vo.setUpdatedAt(new Date());
			});
			List<String> setCloumList = Lists.newArrayList("isGetprize","prizeStatus","updatedAt");
			List<String> whereValueList = Lists.newArrayList("projectId");
			jdbcCreateSqlUtil.batchUpdate(tempList,
					"update "+ noticeReq.getTitle() + "_projects "
							+"set is_getprize = ?,prize_status = ?,updated_at = ? " +
							"where project_id = ?",
					setCloumList,whereValueList);
		}
		if(!sumList.isEmpty()){
			List<BetInfoEntity> tempList = new ArrayList<>();
			tempList.addAll(sumList);
			tempList.forEach(vo -> {
				vo.setBonus(vo.getWinbonus());
				vo.setIsGetprize(1);
				vo.setBonusTime(new Date());
				vo.setUpdateTime(new Date());
				vo.setUpdatedAt(new Date());
			});
			List<String> setCloumList = Lists.newArrayList("bonus","isGetprize","bonusTime","updateTime","updatedAt");
			List<String> whereValueList = Lists.newArrayList("projectId");
			jdbcCreateSqlUtil.batchUpdate(tempList,
					"update "+ noticeReq.getTitle() + "_projects set "
							+"bonus = ?, is_getprize = ?, bonus_time = ?, update_time = ? updated_at = ? " +
							"where project_id = ? and is_cancel = 0",
					setCloumList,whereValueList);
		}
		List<String> userIds = sumList.stream().map(BetInfoEntity::getUserId).collect(Collectors.toSet()).stream().collect(Collectors.toList());
		//删除临时注单记录 1
		List<String> projectIds = list.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
		projectsTmpMapper.deleteBatchIds(projectIds);

		//中奖
		if(userIds == null || userIds.size() == 0){
			return;
		}
		//锁定用户资金
		betInfoMapper.doLockUserFund(noticeReq.getTitle(), userIds,5);
	}

	/**
	 * 更新注单数据
	 * @param sumList
	 * @param noticeReq
	 * @param list
	 */
	public void updateDataAll(List<BetInfoEntity> sumList,NoticeReq noticeReq,List<BetInfoEntity> list,Date bonusTime,String funName){
		//log.info("=========== 更新注单数据  from : {}  title : {}",funName,noticeReq.getTitle());
		//中奖订单ID列表
		List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
		//未中奖订单ID
		List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
				.collect(Collectors.toList());
		if(!notWinList.isEmpty()){
			betInfoMapper.updateByNotWinList(noticeReq, notWinList);
		}
		if(!sumList.isEmpty()){
			betInfoMapper.updateWinbonus(noticeReq,sumList,bonusTime);
		}
		List<String> userIds = sumList.stream().map(BetInfoEntity::getUserId).collect(Collectors.toSet()).stream().collect(Collectors.toList());

		//删除临时注单记录 1
		List<String> projectIds = list.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
		projectsTmpMapper.deleteBatchIds(projectIds);

		//中奖
		if(userIds == null || userIds.size() == 0){
			return;
		}
		//锁定用户资金
		betInfoMapper.doLockUserFund(noticeReq.getTitle(), userIds,5);
		//汇总累加用户奖金
		Map<String, BigDecimal> bonusMap = new HashMap<>();
		for (BetInfoEntity i : sumList) {
			if (bonusMap.containsKey(i.getUserId())) {
				bonusMap.put(i.getUserId(), bonusMap.get(i.getUserId()).add(new BigDecimal(i.getWinbonus())));
			} else {
				bonusMap.put(i.getUserId(), new BigDecimal(i.getWinbonus()));
			}
		}
		//账变写入 orders
		List<String> orderIds = addOrdersReArrayUtil(noticeReq,sumList);

		if(orderIds.isEmpty() || orderIds == null){
			log.info("订单不存在 - 解锁用户资金");
			//解锁用户资金
			betInfoMapper.unLockUserFund(noticeReq, userIds);
			return;
		}

		//生成抄单（Speculation）记录（依业务类型）
		roomMasterMapper.createSpeculation(noticeReq.getRoomMaster(),orderIds);

		//更新用户资金余额
		userFundMapper.updateUserFund(noticeReq, bonusMap);

		//解锁用户资金
		betInfoMapper.unLockUserFund(noticeReq, userIds);
	}


	/**
	 * 更新账单工具
	 * @param noticeReq
	 * @param sumList
	 * @return
	 */
	private List<String> addOrdersReArrayUtil(NoticeReq noticeReq, List<BetInfoEntity> sumList){
		if (sumList == null || sumList.isEmpty()) {
			return null;
		}
		//orderId  entry
		List<String> entryList = new ArrayList<>();

		// 取用户列表
		List<String> userIds = sumList.stream().map(BetInfoEntity::getUserId).distinct().collect(Collectors.toList());

		// 查询钱包（此时你已 doLockUserFund，建议这里加 FOR UPDATE 进一步防并发）
		List<UserFundEntity> fundList = betInfoMapper.selectUserFundBalancesForUpdate(noticeReq, userIds);

		// 初始余额 Map（后面会滚动更新，确保同一用户多条 orders 的 pre/post 正确）
		Map<String, BigDecimal> curChannel = new HashMap<>();
		Map<String, BigDecimal> curAvailable = new HashMap<>();
		Map<String, BigDecimal> curHold = new HashMap<>();

		for (UserFundEntity f : fundList) {
			curChannel.put(f.getUserid(), nz(f.getChannelbalance()));
			curAvailable.put(f.getUserid(), nz(f.getAvailablebalance()));
			curHold.put(f.getUserid(), nz(f.getHoldbalance()));
		}

		// master_id：按你实际字段取（示例写法）
		long masterId = 0L;
		if (noticeReq != null && noticeReq.getRoomMaster() != null) {
			masterId = noticeReq.getRoomMaster().getMasterId();
		}

		Date now = new Date();
		List<OrdersEntity> orders = new ArrayList<>(sumList.size());

		for (BetInfoEntity p : sumList) {
			String uid = p.getUserId();

			// amount = winbonus（你若有 getPrize() 的真实奖金逻辑，可替换这里）
			BigDecimal amount = safeMoney(p.getWinbonus());
			if (amount.compareTo(BigDecimal.ZERO) <= 0) {
				continue; // 0 或负数不写账变（按你业务需要可删）
			}

			BigDecimal preBal = curChannel.getOrDefault(uid, BigDecimal.ZERO);
			BigDecimal preAvail = curAvailable.getOrDefault(uid, BigDecimal.ZERO);
			BigDecimal preHold = curHold.getOrDefault(uid, BigDecimal.ZERO);

			BigDecimal postBal = preBal.add(amount);
			BigDecimal postAvail = preAvail.add(amount);

			// 滚动更新：关键点 ✅
			curChannel.put(uid, postBal);
			curAvailable.put(uid, postAvail);

			OrdersEntity o = new OrdersEntity();
			// A. 直接来自 Project 注单（你 BetInfoEntity 上要有这些字段）
			o.setLotteryId(p.getLotteryId());
			o.setMethodId(p.getMethodId());
			o.setIssue(p.getIssue());
			o.setProjectId(p.getProjectId());
			o.setTaskId(p.getTaskId() == null ? "0" : p.getTaskId());
			o.setModes(p.getModes());
			o.setFromuserId(uid);

			// B. 奖金金额相关
			o.setAmount(amount);
			o.setOrderTypeId(5);                 // ORDER_TYPE_JJPS (=5)
			o.setTitle("奖金派送");
			o.setDescription("奖金派送");

			// C. 用户及钱包字段（由钱包计算）
			o.setPreBalance(preBal);
			o.setPreAvailable(preAvail);
			o.setPreHold(preHold);
			o.setChannelBalance(postBal);
			o.setAvailableBalance(postAvail);
			o.setHoldBalance(preHold);

			// D. 其他字段
			o.setTimes(now);
			o.setActionTime(now);
			o.setCreatedAt(now);
			o.setUpdatedAt(now);

			//订单id
			// entry：str_pad(master_id,3,'0') . uniqid()
			String uuid = String.format("%03d", masterId) + uniqId().substring(0,13);
			entryList.add(uuid);
			o.setEntry(uuid);
			// unique_key：microtime(true)（这里用 毫秒+随机，保证唯一）
			o.setUniqueKey(String.valueOf(System.currentTimeMillis()) + rand4());

			o.setClientIp("0.0.0.0");
			o.setProxyIp("0.0.0.0");

			o.setThirdPartyTrxId("0");
			o.setSendMoneyToPlatform(1); // third_party_trx_id 不为空 → 3，否则 1

			// 默认字段
			o.setTouserId("0");
			o.setAgentId("0");
			o.setAdminId(0);
			o.setAdminName("");
			o.setTransferOrderId("0");
			o.setTransferUserId("0");
			o.setTransferChannelId(0);
			o.setTransferStatus(0);

			orders.add(o);
		}

		if (orders.isEmpty()) {
			return null;
		}
		//log.info("============插入order\nnoticReq:  {}\norders:  {}",noticeReq,orders);
		betInfoMapper.batchInsertOrders(noticeReq, orders);
		return entryList;
	}





	/**
	 * 生成数据-测试
	 */
	public ApiResp<String> createData(Integer count) {
		List<String> uuidList = new ArrayList<>();

		Date currentDate = new Date();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(currentDate);
		calendar.add(Calendar.SECOND, 20); // 加20秒
		Date newDate = calendar.getTime();
		LambdaQueryWrapper<IssueInfoEntity> IssuequeryWrapper =  new LambdaQueryWrapper<>();
		IssuequeryWrapper.eq(IssueInfoEntity::getLotteryId,count)
				.le(IssueInfoEntity::getSaleStart, newDate)  // sale_start <= now
				.gt(IssueInfoEntity::getSaleEnd, newDate);   // sale_end   > now;
		IssueInfoEntity issue = issueInfoMapper.selectOne(IssuequeryWrapper);

		for (int i = 1000; i<1500; i++){
			uuidList.add(uniqId().substring(0,10) + i);
		}

		List<String> titles = new ArrayList<>();
		titles.add("cn0003");
		titles.add("cn0160");
		projectsTmpMapper.createData(uuidList,issue,titles);
		projectsTmpMapper.createIssueData(issue,titles);
		return ApiResp.sucess();
	}


	// ---------- 小工具 ----------
	static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

	static BigDecimal safeMoney(Object winbonus) {
		if (winbonus == null) return BigDecimal.ZERO;
        if (winbonus instanceof BigDecimal) {
            return (BigDecimal) winbonus;
        }
		String s = String.valueOf(winbonus).trim();
		if (s.isEmpty()) return BigDecimal.ZERO;
		return new BigDecimal(s);
	}

	static String uniqId() {
		// 类似 uniqid：时间 + 随机
		return Long.toHexString(System.nanoTime()) + Long.toHexString(ThreadLocalRandom.current().nextLong());
	}

	static String rand4() {
		int r = ThreadLocalRandom.current().nextInt(1000, 10000);
		return String.valueOf(r);
	}

}
