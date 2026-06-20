package com.giving.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.github.pagehelper.PageHelper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.ProjectsTmpMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.mapper.TempIssueInfoMapper;
import com.giving.mapper.UserFundMapper;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.giving.service.OPissueToolService;
import com.giving.service.OrdersToolService;
import com.giving.util.JdbcCreateSqlUtil;
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
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private OrdersToolService ordersToolService;
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;
    @Autowired
    private RoomMasterMapper roomMasterMapper;

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
            if (list == null || list.isEmpty()) {
                //log.info("===========订单查询完毕 page:{}",pageNo);
                break;
            }
            allBetList.add(list);
            pageNo += 1;
        }
        int processedBatchCount = 0;
        ConcurrentMap<String, List<BetInfoEntity>> betRecordMap = Maps.newConcurrentMap();//用户对应订单列表
        List<BetInfoEntity> betAllWinList = Lists.newArrayList();//总中奖订单列表
        for (List<BetInfoEntity> list : allBetList) {
                List<BetInfoEntity> allWinList = Collections.synchronizedList(Lists.newArrayList());
                List<Integer> endList = Collections.synchronizedList(Lists.newArrayList());
                List<Throwable> validationErrors = Collections.synchronizedList(Lists.newArrayList());

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
                                        .filter(vo -> {
                                        	if(vo.getCode().indexOf(key) >= 0 && vo.getMethodCode().equals("2DBZ")) {
                                        		return true;
                                        	}
                                        	
                                        	return false;
                                        })
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            } else if (key.length() == 3) {
                                List<BetInfoEntity> winList = betList.stream()
                                        .filter(vo -> {
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 2, key.length())) >= 0 && vo.getMethodCode().equals("2DBZ")) {
                                        		return true;
                                        	}
                                        	if(vo.getCode().indexOf(key) >= 0 && vo.getMethodCode().equals("3DBZ")) {
                                        		return true;
                                        	}
                                        	return false;
                                        })
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream()
                                        .filter(vo -> {
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 2, key.length())) >= 0 && vo.getMethodCode().equals("2DBZ")) {
                                        		return true;
                                        	}
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 3, key.length())) >= 0 && vo.getMethodCode().equals("3DBZ")) {
                                        		return true;
                                        	}
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 4, key.length())) >= 0 && vo.getMethodCode().equals("4DBZ")) {
                                        		return true;
                                        	}
                                        	return false;
                                        }).collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            }

                        }
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("包组验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
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
                                        .filter(vo -> vo.getCode().indexOf(key) >= 0)
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream().filter(
                                                vo -> {
                                                	if(vo.getCode().indexOf(key.substring(key.length() - 2, key.length())) >= 0) {
                                                		return true;
                                                	}
                                                	return false;
                                                })
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            }
                        }
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("2D包组7验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
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
                                        .filter(vo -> vo.getCode().indexOf(key) >= 0)
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream().filter(
                                                vo -> {
                                                	if(vo.getCode().indexOf(key.substring(key.length() - 3)) >= 0){
                                                		return true;
                                                	}
                                                	return false;
                                                })
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            }
                        }
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("3D包组7验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(3);
                }).start();
                
        		Map<String,Integer> map = Maps.newHashMap();
        		map.put(codeList.get(0)+",",8);
        		map.put(codeList.get(1)+",",7);
        		map.put(codeList.get(2)+","+codeList.get(3)+","+codeList.get(4)+",",6);
        		map.put(codeList.get(5)+",",5);
        		map.put(codeList.get(6)+","+codeList.get(7)+","+codeList.get(8)+","+codeList.get(9)+","+codeList.get(10)+","+codeList.get(11)+","+codeList.get(12)+",",4);
        		map.put(codeList.get(13)+","+codeList.get(14)+",",3);
        		map.put(codeList.get(15)+",",2);
        		map.put(codeList.get(16)+",",1);
        		map.put(codeList.get(17)+",",0);
                new Thread(() -> {// pl2
                    try {
                    	 // 筛选出pl2玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("PL2"))
                                .collect(Collectors.toList());
                        List<BetInfoEntity> winList = betList.stream().filter(vo -> {
                        	String[] groups = vo.getCode().split(",");
                			int num = 0;
                			for(String group : groups) {
                				String[] checkCodes = group.split("&");
                				List<List<Integer>> allIndexList = Lists.newArrayList();
                				for(String checkCode : checkCodes) {
                					List<Integer> indexList = Lists.newArrayList();
                					for(String key : map.keySet()) {
                						if(key.indexOf(checkCode+",") >= 0) {
                							Integer index = map.get(key);
                							if(!indexList.contains(index)) {
                								indexList.add(index);
                							}
                						}
                					}
                					
                					if(indexList.isEmpty()) {
                						break;
                					}
                					Collections.sort(indexList);
                					allIndexList.add(indexList);
                				}
                				if(allIndexList.size() == 2) {
                					String oneIndexStr = JSON.toJSONString(allIndexList.get(0));
                					String twoIndexStr = JSON.toJSONString(allIndexList.get(1));
                					if(!oneIndexStr.equals(twoIndexStr)) {
                						num += 1;
                					}
                				}
                			}
                			
                			if(num > 0) {
                				vo.setBonus(Double.valueOf(vo.getWinbonus()) * num);
                				return true;
                			}
                			return false;
                		}).collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("PL2验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(4);
                }).start();
                new Thread(() -> {// pl3
                    try {
                        // 筛选出pl3玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("PL3"))
                                .collect(Collectors.toList());
                        List<BetInfoEntity> winList = betList.stream().filter(vo -> {
                        	String[] groups = vo.getCode().split(",");
                			int num = 0;
                			for(String group : groups) {
                				String[] checkCodes = group.split("&");
                				List<List<Integer>> allIndexList = Lists.newArrayList();
                				for(String checkCode : checkCodes) {
                					List<Integer> indexList = Lists.newArrayList();
                					for(String key : map.keySet()) {
                						if(key.indexOf(checkCode+",") >= 0) {
                							Integer index = map.get(key);
                							if(!indexList.contains(index)) {
                								indexList.add(index);
                							}
                						}
                					}
                					
                					if(indexList.isEmpty()) {
                						break;
                					}
                					Collections.sort(indexList);
                					allIndexList.add(indexList);
                				}
                				if(allIndexList.size() == 3) {
                					String oneIndexStr = JSON.toJSONString(allIndexList.get(0));
                					String twoIndexStr = JSON.toJSONString(allIndexList.get(1));
                					String threeIndexStr = JSON.toJSONString(allIndexList.get(2));
                					if(!oneIndexStr.equals(twoIndexStr) && !oneIndexStr.equals(threeIndexStr) && !twoIndexStr.equals(threeIndexStr)) {
                						num += 1;
                					}
                				}
                			}
                			
                			if(num > 0) {
                				vo.setBonus(Double.valueOf(vo.getWinbonus()) * num);
                				return true;
                			}
                			return false;
                		}).collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("PL3验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(5);
                }).start();
                new Thread(() -> {//2d头、头尾
                    try {
                        //筛选出2d头、头尾玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DT") ||
                                        vo.getMethodCode().equals("2DTW"))
                                .collect(Collectors.toList());
                        String headCode = codeList.get(0);
                        List<BetInfoEntity> winList = betList.stream().filter(
                                        vo -> {
                                        	if(vo.getCode().indexOf(headCode) >= 0) {
                                        		return true;
                                        	}
                                        	return false;
                                        })
                                .collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("2D头验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(6);
                }).start();
                new Thread(() -> {//2d尾、头尾
                    try {
                        //筛选出2d尾、头尾玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DW") ||
                                        vo.getMethodCode().equals("2DTW"))
                                .collect(Collectors.toList());
                        String endCode = codeList.get(17).substring(4, 6);
                        List<BetInfoEntity> winList = betList.stream().filter(
                                        vo -> {
                                        	if(vo.getCode().indexOf(endCode) >= 0) {
                                        		return true;
                                        	}
                                        	return false;
                                        })
                                .collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("2D尾验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(7);
                }).start();
                new Thread(() -> {//3d头、头尾
                    try {
                        //筛选出3d头、尾、头尾玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DT") ||
                                        vo.getMethodCode().equals("3DTW"))
                                .collect(Collectors.toList());
                        String headCode = codeList.get(1);
                        List<BetInfoEntity> winList = betList.stream().filter(
                                        vo -> {
                                        	if(vo.getCode().indexOf(headCode) >= 0) {
                                        		return true;
                                        	}
                                        	return false;
                                        })
                                .collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("3D头验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(8);
                }).start();
                
                new Thread(() -> {//3d尾、头尾
                    try {
                        //筛选出3d头、尾、头尾玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DW") ||
                                        vo.getMethodCode().equals("3DTW"))
                                .collect(Collectors.toList());
                        String endCode = codeList.get(17).substring(3, 6);
                        List<BetInfoEntity> winList = betList.stream().filter(
                                        vo -> {
                                        	if(vo.getCode().indexOf(endCode) >= 0) {
                                        		return true;
                                        	}
                                        	return false;
                                        })
                                .collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("3D尾验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(9);
                }).start();
                new Thread(() -> {//4D尾玩法
                    try {
                        //筛选出4d尾玩法的订单
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("4DW"))
                                .collect(Collectors.toList());
                        String endCode = codeList.get(17).substring(2, 6);
                        List<BetInfoEntity> winList = betList.stream()
                                .filter(vo -> vo.getCode().indexOf(endCode + ",") >= 0 || vo.getCode().endsWith(endCode))
                                .collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("4D尾验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(10);
                }).start();
                //判断所有子线程是否执行完成
                while (true) {
                    if (endList.size() == 10) {
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!validationErrors.isEmpty()) {
                    throw new IllegalStateException("验奖计算失败，奖期：" + noticeReq.getIssue(),
                            validationErrors.get(0));
                }
                //中奖订单
                List<BetInfoEntity> sumList = getSumList(allWinList);
                betAllWinList.addAll(sumList);
                
                list.forEach(item -> {
                	for(BetInfoEntity bet : sumList) {
                		if(item.getProjectId().equals(bet.getProjectId())) {
                			item.setBonus(bet.getBonus());
                            item.setIsGetprize(1);
                		}
                	}
                });
                
                ConcurrentMap<String, List<BetInfoEntity>> userBetListMap = list.parallelStream()
                        .collect(Collectors.groupingByConcurrent(BetInfoEntity::getUserId));
                userBetListMap.forEach((userId, newList) ->
	                    betRecordMap.merge(userId, new ArrayList<>(newList), (oldList, incomingList) -> {
	                        List<BetInfoEntity> merged = new ArrayList<>(oldList);
	                        merged.addAll(incomingList);
	                        return merged;
	                    })
	                );
              //只记录验奖结果，所有订单验奖完成后再统一派奖
//              updateValidationResult(sumList, noticeReq, list);
                
        }
        this.dataHandle(betRecordMap, betAllWinList, noticeReq);
    }

    @Override
    public void noticeNorth(NoticeReq noticeReq) {
        try {
            Long startTime = System.currentTimeMillis();
            // TODO Auto-generated method stub
            int pageSize = 3000;
            int pageNo = 1;
            // 将开奖号码转换为list
            List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
            int maxSize = codeList.size() - 1;
            Date bonusTime = new Date();

            List<Integer> waitList = Lists.newArrayList();
            ConcurrentMap<String, List<BetInfoEntity>> betRecordMap = Maps.newConcurrentMap();//用户对应订单列表
            List<BetInfoEntity> betAllWinList = Lists.newArrayList();//总中奖订单列表
            while (true) {
                PageHelper.startPage(pageNo, pageSize);
                // TODO Auto-generated method stub
                // 获取对应奖期对应彩种未撤单且未派奖的所有订单
                List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
                if (list.isEmpty()) {
                    break;
                }
                pageNo += 1;
                List<BetInfoEntity> allWinList = Collections.synchronizedList(Lists.newArrayList());
                List<Integer> endList = Collections.synchronizedList(Lists.newArrayList());
                List<Throwable> validationErrors = Collections.synchronizedList(Lists.newArrayList());

                new Thread(() -> {//2D头,尾，头尾玩法
                    try {
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DT")
                                        || vo.getMethodCode().equals("2DW")
                                        || vo.getMethodCode().equals("2DTW"))
                                .collect(Collectors.toList());
                        List<String> headCodeList = codeList.stream().limit(4).collect(Collectors.toList());
                        String endCode = codeList.get(maxSize).substring(3, 5);
                        for (String code : headCodeList) {
                            List<BetInfoEntity> collect = betList.stream().filter(
                                    vo -> {
                                    	if(vo.getCode().indexOf(code) >= 0 && (vo.getMethodCode().equals("2DTW") || vo.getMethodCode().equals("2DT"))) {
                                    		return true;
                                    	}
                                    	return false;
                                    }).collect(Collectors.toList());
                            allWinList.addAll(collect);
                        }
                        List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                        	if(vo.getCode().indexOf(endCode) >= 0 && (vo.getMethodCode().equals("2DTW") || vo.getMethodCode().equals("2DW"))) {
                        		return true;
                        	}
                        	return false;
                        }).collect(Collectors.toList());
                        allWinList.addAll(collect);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("北部彩2D验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(1);
                }).start();

                new Thread(() -> {//3D头,尾，头尾玩法
                    try {
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DT")
                                        || vo.getMethodCode().equals("3DW")
                                        || vo.getMethodCode().equals("3DTW"))
                                .collect(Collectors.toList());
                        List<String> headCodeList = codeList.stream().skip(4).limit(3).collect(Collectors.toList());
                        String endCode = codeList.get(maxSize).substring(2, 5);
                        for (String code : headCodeList) {
                            List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                            	if(vo.getCode().indexOf(code) >= 0 && (vo.getMethodCode().equals("3DTW") || vo.getMethodCode().equals("3DT"))) {
                            		return true;
                            	}
                            	return false;
                            }).collect(Collectors.toList());
                            allWinList.addAll(collect);
                        }
                        List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                        	if(vo.getCode().indexOf(endCode) >= 0 && (vo.getMethodCode().equals("3DTW") || vo.getMethodCode().equals("3DW"))) {
                        		return true;
                        	}
                        	return false;
                        }).collect(Collectors.toList());
                        allWinList.addAll(collect);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("北部彩3D验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(2);
                }).start();

                new Thread(() -> {//4D尾玩法
                    try {
                        //"4D尾"
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("4DW"))
                                .collect(Collectors.toList());
                        String endCode = codeList.get(maxSize).substring(1, 5);
                        List<BetInfoEntity> collect = betList.stream().filter(vo -> vo.getCode().indexOf(endCode) >= 0).collect(Collectors.toList());
                        allWinList.addAll(collect);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("北部彩4D尾验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(3);
                }).start();

                new Thread(() -> {//包组玩法
                    try {
                        Map<String, Long> countMap = codeList.stream()
                                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
                        //										"4D包组"
                        List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("4DBZ")
                                        || vo.getMethodCode().equals("3DBZ")
                                        || vo.getMethodCode().equals("2DBZ"))
                                .collect(Collectors.toList());
                        for (String key : countMap.keySet()) {
                            Long multiple = countMap.get(key);

                            if (key.length() == 2) {
                                List<BetInfoEntity> winList = betList.stream()
                                        .filter(vo -> {
                                        	if(vo.getCode().indexOf(key) >= 0 && vo.getMethodCode().equals("2DBZ")) {
                                        		return true;
                                        	}
                                        	return false;
                                        }) //"2d包组玩法"
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(vo.getBonus() * multiple);
                                });
                                allWinList.addAll(winList);
                            } else if (key.length() == 3) {
                                List<BetInfoEntity> winList = betList.stream()
                                        .filter(vo ->{
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 2, key.length())) >= 0 && vo.getMethodCode().equals("2DBZ")) {
                                        		return true;
                                        	}
                                        	if(vo.getCode().indexOf(key) >= 0 && vo.getMethodCode().equals("3DBZ")) {
                                        		return true;
                                        	}
                                        	return false;
                                        })    //"3d包组玩法"
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(vo.getBonus() * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream()
                                        .filter(vo -> {
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 2, key.length())) >= 0 && vo.getMethodCode().equals("2DBZ")) {
                                        		return true;
                                        	}
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 3, key.length())) >= 0 && vo.getMethodCode().equals("3DBZ")) {
                                        		return true;
                                        	}
                                        	if(vo.getCode().indexOf(key.substring(key.length() - 4, key.length())) >= 0 && vo.getMethodCode().equals("4DBZ")) {
                                        		return true;
                                        	}
                                        	return false;
                                        })    //"3d包组玩法"
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(vo.getBonus() * multiple);
                                });
                                allWinList.addAll(winList);
                            }
                        }
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("北部彩包组验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(4);
                }).start();

                Map<String,Integer> map = Maps.newHashMap();
        		map.put(codeList.get(0)+","+codeList.get(1)+","+codeList.get(2)+","+codeList.get(3)+",",7);
        		map.put(codeList.get(4)+","+codeList.get(5)+","+codeList.get(6)+",",6);
        		map.put(codeList.get(7)+","+codeList.get(8)+","+codeList.get(9)+","+codeList.get(10)+","+codeList.get(11)+","+codeList.get(12)+",",5);
        		map.put(codeList.get(13)+","+codeList.get(14)+","+codeList.get(15)+","+codeList.get(16)+",",4);
        		map.put(codeList.get(17)+","+codeList.get(18)+","+codeList.get(19)+","+codeList.get(20)+","+codeList.get(21)+","+codeList.get(22)+",",3);
        		map.put(codeList.get(23)+","+codeList.get(24)+",",2);
        		map.put(codeList.get(25)+",",1);
        		map.put(codeList.get(26)+",",0);
                new Thread(() -> {//pl2玩法
                    try {
                        // 筛选出pl2玩法的订单
                        List<BetInfoEntity> betList = list.stream()
                                .filter(vo -> "PL2".equals(vo.getMethodCode()) || Integer.valueOf(7).equals(vo.getMethodId()))
                                .collect(Collectors.toList());
                        List<BetInfoEntity> winList = betList.stream().filter(vo -> {
                        	String[] groups = vo.getCode().split(",");
                			int num = 0;
                			for(String group : groups) {
                				String[] checkCodes = group.split("&");
                				List<List<Integer>> allIndexList = Lists.newArrayList();
                				for(String checkCode : checkCodes) {
                					List<Integer> indexList = Lists.newArrayList();
                					for(String key : map.keySet()) {
                						if(key.indexOf(checkCode+",") >= 0) {
                							Integer index = map.get(key);
                							if(!indexList.contains(index)) {
                								indexList.add(index);
                							}
                						}
                					}
                					
                					if(indexList.isEmpty()) {
                						break;
                					}
                					Collections.sort(indexList);
                					allIndexList.add(indexList);
                				}
                				if(allIndexList.size() == 2) {
                					String oneIndexStr = JSON.toJSONString(allIndexList.get(0));
                					String twoIndexStr = JSON.toJSONString(allIndexList.get(1));
                					if(!oneIndexStr.equals(twoIndexStr)) {
                						num += 1;
                					}
                				}
                			}
                			
                			if(num > 0) {
                				vo.setBonus(Double.valueOf(vo.getWinbonus()) * num);
                				return true;
                			}
                			return false;
                		}).collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("北部彩PL2验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(5);
                }).start();

                new Thread(() -> {//pl3玩法
                    try {
                        // 筛选出pl3玩法的订单
                        List<BetInfoEntity> betList = list.stream()
                                .filter(vo -> "PL3".equals(vo.getMethodCode()) || Integer.valueOf(8).equals(vo.getMethodId()))
                                .collect(Collectors.toList());
                        List<BetInfoEntity> winList = betList.stream().filter(vo -> {
                        	String[] groups = vo.getCode().split(",");
                			int num = 0;
                			for(String group : groups) {
                				String[] checkCodes = group.split("&");
                				List<List<Integer>> allIndexList = Lists.newArrayList();
                				for(String checkCode : checkCodes) {
                					List<Integer> indexList = Lists.newArrayList();
                					for(String key : map.keySet()) {
                						if(key.indexOf(checkCode+",") >= 0) {
                							Integer index = map.get(key);
                							if(!indexList.contains(index)) {
                								indexList.add(index);
                							}
                						}
                					}
                					
                					if(indexList.isEmpty()) {
                						break;
                					}
                					Collections.sort(indexList);
                					allIndexList.add(indexList);
                				}
                				if(allIndexList.size() == 3) {
                					String oneIndexStr = JSON.toJSONString(allIndexList.get(0));
                					String twoIndexStr = JSON.toJSONString(allIndexList.get(1));
                					String threeIndexStr = JSON.toJSONString(allIndexList.get(2));
                					if(!oneIndexStr.equals(twoIndexStr) && !oneIndexStr.equals(threeIndexStr) && !twoIndexStr.equals(threeIndexStr)) {
                						num += 1;
                					}
                				}
                			}
                			
                			if(num > 0) {
                				vo.setBonus(Double.valueOf(vo.getWinbonus()) * num);
                				return true;
                			}
                			return false;
                		}).collect(Collectors.toList());
                        allWinList.addAll(winList);
                    } catch (Exception e) {
                        validationErrors.add(e);
                        log.error("北部彩PL3验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                                noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                    }
                    endList.add(6);
                }).start();

                //判断所有子线程是否执行完成
                while (true) {
                    if (endList.size() == 6) {
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!validationErrors.isEmpty()) {
                    throw new IllegalStateException("北部彩验奖计算失败，奖期：" + noticeReq.getIssue(),
                            validationErrors.get(0));
                }
                //中奖订单
                List<BetInfoEntity> sumList = getSumList(allWinList);
                betAllWinList.addAll(sumList);
                
                list.forEach(item -> {
                	for(BetInfoEntity bet : sumList) {
                		if(item.getProjectId().equals(bet.getProjectId())) {
                			item.setBonus(bet.getBonus());
                            item.setIsGetprize(1);
                		}
                	}
                });
                
                ConcurrentMap<String, List<BetInfoEntity>> userBetListMap = list.parallelStream()
                        .collect(Collectors.groupingByConcurrent(BetInfoEntity::getUserId));
                userBetListMap.forEach((userId, newList) ->
	                    betRecordMap.merge(userId, new ArrayList<>(newList), (oldList, incomingList) -> {
	                        List<BetInfoEntity> merged = new ArrayList<>(oldList);
	                        merged.addAll(incomingList);
	                        return merged;
	                    })
	                );
//                updateDataAll(sumList, noticeReq, list, bonusTime);
                waitList.add(1);
            }

            while (true) {
                if (waitList.size() == (pageNo - 1)) {
                    break;
                }
                Thread.sleep(100);
            }

            this.dataHandle(betRecordMap, betAllWinList, noticeReq);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void noticeTh(NoticeReq noticeReq) {
        Long startTime = System.currentTimeMillis();
        // TODO Auto-generated method stub
        int pageSize = 3000;
        int pageNo = 1;
        // 将开奖号码转换为list
        List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
        int maxSize = codeList.size() - 1;
        Date bonusTime = new Date();
        List<Integer> waitList = new ArrayList<>();
        ConcurrentMap<String, List<BetInfoEntity>> betRecordMap = Maps.newConcurrentMap();//用户对应订单列表
        List<BetInfoEntity> betAllWinList = Lists.newArrayList();//总中奖订单列表
        while (true) {
            PageHelper.startPage(1, pageSize);
            // TODO Auto-generated method stub
            // 获取对应奖期对应彩种未撤单且未派奖的所有订单
            List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
            if (list.isEmpty()) {
                break;
            }
            pageNo += 1;
            List<BetInfoEntity> allWinList = Collections.synchronizedList(Lists.newArrayList());
            List<Integer> endList = Collections.synchronizedList(Lists.newArrayList());
            List<Throwable> validationErrors = Collections.synchronizedList(Lists.newArrayList());
            //1D头、2D头、3D头、1D尾，2D尾
            new Thread(() -> {
                try {
                    List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("1DT") || vo.getMethodCode().equals("1DW")
                            || vo.getMethodCode().equals("2DT") || vo.getMethodCode().equals("2DW")
                            || vo.getMethodCode().equals("3DT")).collect(Collectors.toList());
                    String headCode = codeList.get(0).substring(3, 6);
                    String headCode1 = codeList.get(0).substring(4, 6);
                    String endCode = codeList.get(maxSize);

                    List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                    	if(headCode.indexOf(vo.getCode()) >= 0 && (vo.getMethodCode().equals("1DT") || vo.getMethodCode().equals("3DT"))) {
                    		return true;
                    	}
                    	if(headCode1.indexOf(vo.getCode()) >= 0 && vo.getMethodCode().equals("2DT")) {
                    		return true;
                    	}
                    	if(endCode.indexOf(vo.getCode()) >= 0 && (vo.getMethodCode().equals("2DW") || vo.getMethodCode().equals("1DW"))) {
                    		return true;
                    	}
                    	return false;
                    }).collect(Collectors.toList());
                    allWinList.addAll(collect);
                } catch (Exception e) {
                    validationErrors.add(e);
                    log.error("泰国彩头尾验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                            noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                }
                endList.add(1);
            }).start();

            //3D前三、3D后三
            new Thread(() -> {
                try {
                    List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DHS") || vo.getMethodCode().equals("3DQS")).collect(Collectors.toList());
                    String frontThreeCode = codeList.get(1) + codeList.get(2);
                    String afterThreeCode = codeList.get(3) + codeList.get(4);

                    List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                    	if((frontThreeCode.startsWith(vo.getCode()) || frontThreeCode.endsWith(vo.getCode())) && vo.getMethodCode().equals("3DQS")) {
                    		return true;
                    	}
                    	if((afterThreeCode.startsWith(vo.getCode()) || afterThreeCode.endsWith(vo.getCode())) && vo.getMethodCode().equals("3DHS")) {
                    		return true;
                    	}
                    	return false;
                    }).collect(Collectors.toList()); //"3D后三"
                    allWinList.addAll(collect);
                } catch (Exception e) {
                    validationErrors.add(e);
                    log.error("泰国彩3D前后验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                            noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                }
                endList.add(2);
            }).start();
            //2D头奖组选  3D头奖组选
            new Thread(() -> {
                try {
                    List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DTJZX") || vo.getMethodCode().equals("3DTJZX")).collect(Collectors.toList());
                    String headCode = codeList.get(0).substring(3, 6);
                    char[] arr = headCode.toCharArray();
                    Arrays.sort(arr);
                    String result = new String(arr);
                    List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                    	char[] codeArr = vo.getCode().toCharArray();
                        Arrays.sort(codeArr);
                        String newCode = new String(codeArr);
                        
                        if(result.indexOf(newCode) >= 0) {
                        	return true;
                        }
                    	return false;
                    }).collect(Collectors.toList());
                    allWinList.addAll(collect);
                } catch (Exception e) {
                    validationErrors.add(e);
                    log.error("泰国彩组选验奖失败，厅主表名={}，彩种ID={}，奖期={}",
                            noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), e);
                }
                endList.add(3);
            }).start();

            //判断所有子线程是否执行完成
            while (true) {
                if (endList.size() == 3) {
                    break;
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!validationErrors.isEmpty()) {
                throw new IllegalStateException("泰国彩验奖计算失败，奖期：" + noticeReq.getIssue(),
                        validationErrors.get(0));
            }
            //中奖订单
            List<BetInfoEntity> sumList = getSumList(allWinList);
            betAllWinList.addAll(sumList);
            
            list.forEach(item -> {
            	for(BetInfoEntity bet : sumList) {
            		if(item.getProjectId().equals(bet.getProjectId())) {
            			item.setBonus(bet.getBonus());
                        item.setIsGetprize(1);
            		}
            	}
            });
            
            ConcurrentMap<String, List<BetInfoEntity>> userBetListMap = list.parallelStream()
                    .collect(Collectors.groupingByConcurrent(BetInfoEntity::getUserId));
            userBetListMap.forEach((userId, newList) ->
                    betRecordMap.merge(userId, new ArrayList<>(newList), (oldList, incomingList) -> {
                        List<BetInfoEntity> merged = new ArrayList<>(oldList);
                        merged.addAll(incomingList);
                        return merged;
                    })
                );
            waitList.add(1);
        }
        while (true) {
            if (waitList.size() == (pageNo - 1)) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        
        this.dataHandle(betRecordMap, betAllWinList, noticeReq);
    }

    @Override
    public void noticeLw(NoticeReq noticeReq) {
        Long startTime = System.currentTimeMillis();
        Date bonusTime = new Date();
        // TODO Auto-generated method stub
        int pageSize = 3000;
        int pageNo = 1;
        List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
        String headCode = codeList.get(0);
        String headCode2 = headCode.substring(1, 3);
        String endCode = codeList.get(1);
        List<Integer> waitList = new ArrayList<>();
        ConcurrentMap<String, List<BetInfoEntity>> betRecordMap = Maps.newConcurrentMap();//用户对应订单列表
        List<BetInfoEntity> betAllWinList = Lists.newArrayList();//总中奖订单列表
        while (true) {
            PageHelper.startPage(1, pageSize);
            // TODO Auto-generated method stub
            // 获取对应奖期对应彩种未撤单且未派奖的所有订单
            List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
            if (list.isEmpty()) {
                break;
            }
            pageNo += 1;
            // 将开奖号码转换为list
            List<BetInfoEntity> allWinList = Lists.newArrayList();
            //1D头、尾 2D头尾,3D头校验 1DT 1DW 2DT 2DW 3DT
            List<BetInfoEntity> collect = list.stream().filter(vo -> {
            	if(headCode.indexOf(vo.getCode()) >= 0 && ("1DT".equals(vo.getMethodCode()) || "3DT".equals(vo.getMethodCode()))) {
            		return true;
            	}
            	if(headCode2.indexOf(vo.getCode()) >= 0 && "2DT".equals(vo.getMethodCode())) {
            		return true;
            	}
            	if(endCode.indexOf(vo.getCode()) >= 0 && ("1DW".equals(vo.getMethodCode()) || "2DW".equals(vo.getMethodCode()))) {
            		return true;
            	}
            	return false;
            }).collect(Collectors.toList());
            allWinList.addAll(collect);

            char[] a = headCode.toCharArray();
            java.util.Arrays.sort(a);
            String tjzx = new String(a);
            //3D组选
            List<BetInfoEntity> collect2 = list.stream().filter(vo -> {
                char[] codeArr = vo.getCode().toCharArray();
                java.util.Arrays.sort(codeArr);
                String tjzxCode = new String(codeArr);
                return "3DTJZX".equals(vo.getMethodCode()) && tjzx.equals(tjzxCode);
            }).collect(Collectors.toList());
            allWinList.addAll(collect2);
            List<BetInfoEntity> sumList = getSumList(allWinList);
            betAllWinList.addAll(sumList);
            list.forEach(item -> {
            	for(BetInfoEntity bet : sumList) {
            		if(item.getProjectId().equals(bet.getProjectId())) {
            			item.setBonus(bet.getBonus());
                        item.setIsGetprize(1);
            		}
            	}
            });
            
            ConcurrentMap<String, List<BetInfoEntity>> userBetListMap = list.parallelStream()
                    .collect(Collectors.groupingByConcurrent(BetInfoEntity::getUserId));
            userBetListMap.forEach((userId, newList) ->
                    betRecordMap.merge(userId, new ArrayList<>(newList), (oldList, incomingList) -> {
                        List<BetInfoEntity> merged = new ArrayList<>(oldList);
                        merged.addAll(incomingList);
                        return merged;
                    })
                );
        }
        this.dataHandle(betRecordMap, betAllWinList, noticeReq);
    
    }

    @Override
    public void noticeKs(NoticeReq noticeReq) {
        Long startTime = System.currentTimeMillis();
        Date bonusTime = new Date();
        // TODO Auto-generated method stub
        int pageSize = 3000;
        int pageNo = 1;
        List<String> codeList = Lists.newArrayList(noticeReq.getCode().split(","));
        String sortCode = sortChars(noticeReq.getCode().replace(",", "")); //排序过后的code
        String winCodeString = sortCode.substring(0,2)+","+sortCode.substring(0,1)+sortCode.substring(2,3)+","+sortCode.substring(1,3);
        Set<String> set = new HashSet<>(codeList);
        List<String> codeListOnly = new ArrayList<>(set); //去重过后的号码list 用于判断猜一个号 和三不同号

        String removeFirst = sortCode.substring(1);
        String removeLast = sortCode.substring(0, sortCode.length() - 1);
        int sum = 0;  //和值
        for (char c : noticeReq.getCode().replace(",", "").toCharArray()) {
            sum += (c - '0');   // '1'->1, '2'->2 ...
        }
        String sumStr = (sum < 10) ? ("0" + sum) : String.valueOf(sum);
        ConcurrentMap<String, List<BetInfoEntity>> betRecordMap = Maps.newConcurrentMap();//用户对应订单列表
        List<BetInfoEntity> betAllWinList = Lists.newArrayList();//总中奖订单列表
        while (true) {
            PageHelper.startPage(pageNo, pageSize);
            // TODO Auto-generated method stub
            // 获取对应奖期对应彩种未撤单且未派奖的所有订单
            List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
            if (list.isEmpty()) {
                break;
            }
            pageNo += 1;
            int finalSum = sum;
            List<BetInfoEntity> allWinList = list.stream().filter(vo -> {
                //猜一个号
                if("CYGH".equals(vo.getMethodCode()) && (vo.getCode().contains(codeList.get(0)) || vo.getCode().contains(codeList.get(1)) || vo.getCode().contains(codeList.get(2)))){
                    int n = 0;
                    for (String code : codeListOnly) {
                        if(vo.getCode().contains(code)){ n++;}
                    }
                    vo.setBonus(Double.parseDouble(vo.getWinbonus()) * n);
                    return true;
                }
                //和值
                if("HZ".equals(vo.getMethodCode()) && vo.getCode().contains(sumStr)){
                    List<String> winbonusList = Lists.newArrayList(vo.getWinbonus().split(","));
                    vo.setBonus(Double.valueOf(winbonusList.get(finalSum -3)));
                    return true;
                }
                //二不同号
                if("EBTH".equals(vo.getMethodCode()) && (vo.getCode().contains(removeFirst) || vo.getCode().contains(removeLast))){
                    String[] uCodeList = vo.getCode().split("\\|");
                    int ns = 0;
                    for (String code : uCodeList) {
                        if(winCodeString.indexOf(code)>=0){
                            ns+=1;
                        }
//                        ns+=1;
                    }
                    vo.setBonus(Double.valueOf(vo.getWinbonus())*ns);
                    return  true;
                }

                if(("STH".equals(vo.getMethodCode()) && vo.getCode().contains(sortCode)) //三同号
                        || ("SBTH".equals(vo.getMethodCode()) && codeListOnly.size() == 3 && (vo.getCode().contains(codeList.get(0)) && vo.getCode().contains(codeList.get(1)) && vo.getCode().contains(codeList.get(2)))) //三不同号
                        || ("DX".equals(vo.getMethodCode()) && vo.getCode().contains(sortCode)) //二同号-单选
                        || ("FX".equals(vo.getMethodCode()) && (vo.getCode().contains(removeFirst) || vo.getCode().contains(removeLast)))) {
                    vo.setBonus(Double.valueOf(vo.getWinbonus()));
                    return true;
                } //二同号-复选
                return false;
            }).collect(Collectors.toList());
            betAllWinList.addAll(allWinList);
            list.forEach(item -> {
            	for(BetInfoEntity bet : allWinList) {
            		if(item.getProjectId().equals(bet.getProjectId())) {
            			item.setBonus(bet.getBonus());
                        item.setIsGetprize(1);
            		}
            	}
            });
            
            ConcurrentMap<String, List<BetInfoEntity>> userBetListMap = list.parallelStream()
                    .collect(Collectors.groupingByConcurrent(BetInfoEntity::getUserId));
            userBetListMap.forEach((userId, newList) ->
                    betRecordMap.merge(userId, new ArrayList<>(newList), (oldList, incomingList) -> {
                        List<BetInfoEntity> merged = new ArrayList<>(oldList);
                        merged.addAll(incomingList);
                        return merged;
                    })
                );
        }
        this.dataHandle(betRecordMap, betAllWinList, noticeReq);
    }


    private List<BetInfoEntity> getSumList(List<BetInfoEntity> allWinList) {
        return allWinList.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(
                                BetInfoEntity::getProjectId,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        group -> {
                                            // 获取第一条记录
                                            BetInfoEntity first = group.get(0);
                                            Double totalScore = 0.0;
                                            if (first.getBonus() <= 0) {
                                                // 计算总分
                                                totalScore= group.stream()
                                                        .map(BetInfoEntity::getWinbonus)
                                                        .mapToDouble(Double::parseDouble)
                                                        .sum();
                                            }else {
                                                totalScore= group.stream()
                                                        .mapToDouble(BetInfoEntity::getBonus)
                                                        .sum();
                                            }
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


    /**
     * 更新注单数据
     *
     * @param sumList
     * @param noticeReq
     * @param list
     */
    public void updateDataAll(List<BetInfoEntity> sumList, NoticeReq noticeReq, List<BetInfoEntity> list, Date bonusTime) {
        try {
            String title = noticeReq.getTitle();
            //中奖订单-新增order 5 并加钱
            Boolean awardSuccess = ordersToolService.getOrdersListAll(
                    sumList, noticeReq.getTitle(), 5, noticeReq.getRoomMaster());
            if (!Boolean.TRUE.equals(awardSuccess)) {
                throw new IllegalStateException("奖金派发失败，奖期：" + noticeReq.getIssue());
            }
            List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
            //未中奖订单ID
            List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
                    .collect(Collectors.toList());
            //批量修改未中奖订单
            if (!notWinList.isEmpty()) {
                betInfoMapper.updateIsGetprize2(notWinList, title);
            }

            //删除临时注单记录 1
            List<String> projectIds = list.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
            projectsTmpMapper.deleteBatchIds(projectIds);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        LambdaQueryWrapper<IssueInfoEntity> IssuequeryWrapper = new LambdaQueryWrapper<>();
        IssuequeryWrapper.eq(IssueInfoEntity::getLotteryId, count)
                .le(IssueInfoEntity::getSaleStart, newDate)  // sale_start <= now
                .gt(IssueInfoEntity::getSaleEnd, newDate);   // sale_end   > now;
        IssueInfoEntity issue = issueInfoMapper.selectOne(IssuequeryWrapper);

        for (int i = 1000; i < 3000; i++) {
            uuidList.add(uniqId().substring(0, 10) + i);
        }

        List<String> titles = new ArrayList<>();
//        titles.add("cn0003");
        titles.add("cn0160");
        projectsTmpMapper.createData(uuidList, issue, titles);
        projectsTmpMapper.createIssueData(issue, titles);
        return ApiResp.sucess();
    }

    // ---------- 小工具 ----------
    static String uniqId() {
        // 类似 uniqid：时间 + 随机
        return Long.toHexString(System.nanoTime()) + Long.toHexString(ThreadLocalRandom.current().nextLong());
    }

    static String sortChars(String s) {
        char[] arr = s.toCharArray();
        Arrays.sort(arr);
        return new String(arr);
    }
    
    private void dataHandle(ConcurrentMap<String, List<BetInfoEntity>> betRecordMap,List<BetInfoEntity> betAllWinList, NoticeReq noticeReq) {
        Long startTime = System.currentTimeMillis();
        if(betRecordMap.isEmpty()) {
            log.info("奖期：{},表头:{}没有投注记录",noticeReq.getIssue(),noticeReq.getTitle());
            return;
        }
        TempIssueInfoEntity tempIssueInfoEntity = tempIssueInfoMapper.selectByTitle(noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue());
        if(ObjectUtils.isEmpty(tempIssueInfoEntity) || tempIssueInfoEntity.getStatusDeduct() != 0) {
            return;
        }
        tempIssueInfoEntity.setStatusDeduct(1);
        tempIssueInfoMapper.updateById(tempIssueInfoEntity);
        //用户钱包上锁
        for(String userId : betRecordMap.keySet()) {
//    		updateWalletLocked(userId, noticeReq.getTitle(), "[java]充提上锁", 1, 0, 0);
//    		updateWalletLocked(userId, noticeReq.getTitle(), "[java]投注上锁", 1, 0, 1);
//    		updateWalletLocked(userId, noticeReq.getTitle(), "[java]验派上锁", 1, 0, 2);
//    		updateWalletLocked(userId, noticeReq.getTitle(), "[java]撤单上锁", 1, 0, 3);
    		updateWalletLocked(userId, noticeReq.getTitle(), "[java]扣款上锁", 1, 0, 4);
        	updateWalletLocked(userId, noticeReq.getTitle(), "[java]派奖上锁", 1, 0, 5);
    	}
    	
        if(!betAllWinList.isEmpty()) {
            List<BetInfoEntity> sumList = getSumList(betAllWinList);
            betInfoMapper.updateWinResult(noticeReq.getTitle(), sumList);
        }
    	 // 1. 把未校验订单修改为未中奖
        betInfoMapper.updateIsGetprizeTo2(noticeReq.getIssue().trim(), noticeReq.getTitle(),noticeReq.getLotteryId());
    	
    	Integer betNum = 0;
    	
        Map<String,List<OrdersEntity>> chargeMap = Maps.newConcurrentMap();//扣款账变集合
        Map<String,List<OrdersEntity>> prizeMap = Maps.newConcurrentMap();//派奖账变集合
        Map<String, Double> betMap = Maps.newConcurrentMap();// 用户对应扣款总额
        Map<String, Double> winMap = Maps.newConcurrentMap();// 用户对应中奖总额
        //组装扣款账变集合和派奖账变集合
        for(String userId : betRecordMap.keySet()) {
            // 钱包汇总
            UserFundEntity wallet = userFundMapper.selectByUserSum(noticeReq.getTitle(), userId);
            if(ObjectUtils.isEmpty(wallet)) {
            	continue;
            }

            List<BetInfoEntity> list = betRecordMap.get(userId);
            list.sort(Comparator.comparing(BetInfoEntity::getCreatedAt));
            List<OrdersEntity> chargeList = Lists.newArrayList();
            Date date = new Date();
            //处理结算
            Double amt = 0.00;//扣款总额
            for(BetInfoEntity project : list) {
                BigDecimal channelbalance = wallet.getChannelbalance();
                BigDecimal holdbalance = wallet.getHoldbalance();
                BigDecimal availablebalance = wallet.getAvailablebalance();

                //添加账变记录
                OrdersEntity order = new OrdersEntity();
                String uuid =OrdersToolServiceImpl.uniqId16();
                order.setEntry(uuid);
                order.setLotteryId(project.getLotteryId());
                order.setMethodId(project.getMethodId());
                order.setTaskId(project.getTaskId());
                order.setProjectId(project.getProjectId());
                order.setFromuserId(project.getUserId());
                order.setOrderTypeId(8);
                order.setIssue(project.getIssue());
                order.setTitle("游戏扣款");
                order.setAmount(BigDecimal.valueOf(project.getTotalPrice()));
                order.setDescription("游戏扣款");
                order.setPreBalance(channelbalance);     //账变前 --帐变前频道-资金
                order.setPreHold(holdbalance);           //账变前 --帐变前频道-冻结资金
                order.setPreAvailable(availablebalance); //账变前 --帐变前频道-可用资金

                wallet.setChannelbalance(channelbalance.subtract(BigDecimal.valueOf(project.getTotalPrice())));
                wallet.setHoldbalance(holdbalance.subtract(BigDecimal.valueOf(project.getTotalPrice())));


                order.setChannelBalance(wallet.getChannelbalance());        //账变后 --帐变后-资金
                order.setHoldBalance(wallet.getHoldbalance());              //账变后 --帐变后-冻结资金
                order.setAvailableBalance(wallet.getAvailablebalance());      //账变后 --帐变后-可用资金

                order.setUniqueKey(String.valueOf(System.currentTimeMillis()));
                order.setModes(project.getModes());
                order.setCreatedAt(date);
                order.setUpdatedAt(date);
                order.setActionTime(date);
                chargeList.add(order);
                betNum += 1;
                amt += project.getTotalPrice();
        	}
            betMap.put(userId, amt);
            chargeMap.put(userId, chargeList);
            List<BetInfoEntity> winList = list.stream().filter(vo -> vo.getIsGetprize() == 1).collect(Collectors.toList());
            List<OrdersEntity> prizeList = Lists.newArrayList();

            //处理派奖
            Double amt1 = 0.00;//中奖总额
            for(BetInfoEntity project : winList) {
                BigDecimal channelbalance       = wallet.getChannelbalance();
                BigDecimal holdbalance          = wallet.getHoldbalance();
                BigDecimal availablebalance     = wallet.getAvailablebalance();


                //添加账变记录
                OrdersEntity order = new OrdersEntity();
                String uuid =OrdersToolServiceImpl.uniqId16();
                order.setEntry(uuid);
                order.setLotteryId(project.getLotteryId());
                order.setMethodId(project.getMethodId());
                order.setTaskId(project.getTaskId());
                order.setProjectId(project.getProjectId());
                order.setFromuserId(project.getUserId());
                order.setOrderTypeId(5);
                order.setIssue(project.getIssue());
                order.setTitle("奖金派送");
                order.setAmount(BigDecimal.valueOf(project.getBonus()));
                order.setDescription("奖金派送");
                order.setPreBalance(channelbalance);     //账变前 --帐变前频道-资金
                order.setPreHold(holdbalance);           //账变前 --帐变前频道-冻结资金
                order.setPreAvailable(availablebalance); //账变前 --帐变前频道-可用资金

                wallet.setChannelbalance(channelbalance.add(BigDecimal.valueOf(project.getBonus())));
                wallet.setAvailablebalance(availablebalance.add(BigDecimal.valueOf(project.getBonus())));


                order.setChannelBalance(wallet.getChannelbalance());        //账变后 --帐变后-资金
                order.setHoldBalance(wallet.getHoldbalance());              //账变后 --帐变后-冻结资金
                order.setAvailableBalance(wallet.getAvailablebalance());      //账变后 --帐变后-可用资金

                order.setUniqueKey(String.valueOf(System.currentTimeMillis()));
                order.setModes(project.getModes());
                order.setCreatedAt(new Date(date.getTime() + 1000));
                order.setUpdatedAt(new Date(date.getTime() + 1000));
                order.setActionTime(new Date(date.getTime() + 1000));
                prizeList.add(order);
                amt1 += project.getBonus();
            }
            winMap.put(userId, amt1);
            prizeMap.put(userId, prizeList);
        }

        //获取所有需要操作的钱包
        Map<String, UserFundEntity> updateFundMap = Maps.newConcurrentMap();
        for(String userId : betRecordMap.keySet()) {
            UserFundEntity chargeFund = getUserFund(noticeReq.getTitle(),userId,4);
            if(betMap.containsKey(userId)) {
                Double chargeAmt = betMap.get(userId);
                if(chargeAmt > 0) {
                    chargeFund.setChannelbalance(chargeFund.getChannelbalance().subtract(BigDecimal.valueOf(chargeAmt)));
                    chargeFund.setHoldbalance(chargeFund.getHoldbalance().subtract(BigDecimal.valueOf(chargeAmt)));
                    updateFundMap.put(userId+"4", chargeFund);
                }
            }
            UserFundEntity prizeFund = getUserFund(noticeReq.getTitle(),userId,5);
            if(winMap.containsKey(userId)) {
                Double prizeAmt = winMap.get(userId);
                if(prizeAmt > 0) {
                    prizeFund.setChannelbalance(prizeFund.getChannelbalance().add(BigDecimal.valueOf(prizeAmt)));
                    prizeFund.setAvailablebalance(prizeFund.getAvailablebalance().add(BigDecimal.valueOf(prizeAmt)));
                    updateFundMap.put(userId+"5", prizeFund);
                }
            }
        }

        //数据库操作
        for(String userId : betRecordMap.keySet()) {
            List<OrdersEntity> ordersList = new ArrayList<>();  //需要新增的orders
            if(chargeMap.containsKey(userId)) {
                List<OrdersEntity> list = chargeMap.get(userId);
                ordersList.addAll(list);
            }

            if(prizeMap.containsKey(userId)) {
                List<OrdersEntity> list = prizeMap.get(userId);
                ordersList.addAll(list);
            }

            if(!ordersList.isEmpty()) {
                int insertedOrderCount = ordersMapper.addOrdersListAll(ordersList,noticeReq.getTitle());
                if(insertedOrderCount != ordersList.size()){
                    throw new RuntimeException("插入账变失败，应插入：" + ordersList.size()
                            + "，实际插入：" + insertedOrderCount);
                }
            }
        }

        //更新用户钱包
        if(!updateFundMap.isEmpty()) {
            int updatedWalletCount = userFundMapper.doUpdateAddOrdersList(noticeReq.getTitle(),updateFundMap);
            if(updatedWalletCount != updateFundMap.size()) {
                throw new RuntimeException("批量修改钱包失败，应更新：" + updateFundMap.size()
                        + "，实际更新：" + updatedWalletCount);
            }
        }

        //修改当前订单派奖时间
        betInfoMapper.updatePrize(noticeReq.getTitle(), noticeReq.getIssue().trim(),noticeReq.getLotteryId());

        //用户钱包解锁
        for(String userId : betRecordMap.keySet()) {
//    		updateWalletLocked(userId, noticeReq.getTitle(), "充提解锁", 0, 1, 0);
//    		updateWalletLocked(userId, noticeReq.getTitle(), "投注解锁", 0, 1, 1);
//    		updateWalletLocked(userId, noticeReq.getTitle(), "验派解锁", 0, 1, 2);
//    		updateWalletLocked(userId, noticeReq.getTitle(), "撤单解锁", 0, 1, 3);
            updateWalletLocked(userId, noticeReq.getTitle(), "[java]扣款解锁", 0, 1, 4);
            updateWalletLocked(userId, noticeReq.getTitle(), "[java]派奖解锁", 0, 1, 5);
        }

        //单钱包处理
        RoomMasterEntity roomMaster = noticeReq.getRoomMaster();
        if (roomMaster.getUserWalletType() == 1){

            for(String userId : betRecordMap.keySet()) {
                if(prizeMap.containsKey(userId)) {
                    List<OrdersEntity> list = prizeMap.get(userId);
                    if(!list.isEmpty()) {
                        roomMasterMapper.createSpeculationList(roomMaster,list);
                    }
                }
            };
        }

        tempIssueInfoEntity.setStatusDeduct(2);
        tempIssueInfoMapper.updateById(tempIssueInfoEntity);
        Long endTime = System.currentTimeMillis();
        log.info("\n============={}=================" +
                "\nlotteryId = {}" +
                "\nissue = {}" +
                "\n注单数:{}" +
                "\n开始时间:{}" +
                "\n结束时间:{}" +
                "\n耗时:{}" +
                "\n============={}=================", noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), betNum, startTime, endTime, endTime - startTime);
    }

    /**
     * 获取钱包
     * @param title 表头
     * @param userId 用户ID
     * @param walletType 钱包类别 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款 ,5 派奖
     * @return
     */
    private UserFundEntity getUserFund(String title,String userId,int walletType) {
    	UserFundEntity o = new UserFundEntity();
        o.setUserid(userId);
        o.setWalletType(walletType);
        UserFundEntity userFundEntity = userFundMapper.selectByUserAndTypeOne(title, o); //频道钱包
        if(ObjectUtils.isEmpty(userFundEntity)) {
        	log.info("用户:" + userId+"，表头:"+title+"未查询到类型为："+walletType+"钱包");
//        	throw new RuntimeException("用户:" + userId+"获取类型为："+walletType+"钱包失败");
        }
        return userFundEntity;
    }
    
    /**
	 * 修改钱包状态
	 * @param userid 用户ID
	 * @param title 表头
	 * @param lockAction 操作说明
	 * @param islocked 是否上锁 0=正常, 1=被锁
	 * @param nowIsLock 当前锁定状态
	 * @param walletType 钱包类别 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款 ,5 派奖
	 * @return
	 */
	private void updateWalletLocked(String userid,String title, String lockAction, int islocked,  int nowIsLock,int walletType) {
		int count = 0;
    	while(true) {
    		UserFundEntity wallet = userFundMapper.selectByUserSum(title, userid);
    		if(ObjectUtils.isEmpty(wallet)) {
    			log.info("用户:" + userid+"，表头:"+title+"未查询到钱包");
    			return;
    		}
    		int lockStatus = userFundMapper.updateWalletLocked(userid, title, lockAction, islocked, nowIsLock, walletType);
    		if(lockStatus > 0) {
    			break;
    		}
    		count ++ ;
    		
    		if(count == 20) {
    			throw new RuntimeException("用户:" + userid+"，表头:"+title+"尝试"+count+"后"+lockAction+"失败");
    		}
    		
    		try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }

}
