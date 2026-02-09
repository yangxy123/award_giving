package com.giving.service.impl;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.mapper.*;
import com.giving.req.ManualDistributionReq;
import com.giving.service.OPissueToolService;
import com.giving.service.OrdersToolService;
import com.giving.service.UserFundLockTxService;
import com.giving.util.JdbcCreateSqlUtil;
import com.giving.util.RedisUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private OrdersToolService ordersToolService;
    @Autowired
    private JdbcCreateSqlUtil jdbcCreateSqlUtil;
    @Autowired
    private OPissueToolService oPissueToolService;

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
        for (List<BetInfoEntity> list : allBetList) {
            int i = count;
            new Thread(() -> {
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
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
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
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
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
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
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
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream().filter(
                                                vo -> vo.getCode().indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
                                                        || vo.getCode().endsWith(key.substring(key.length() - 2, key.length())))
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
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
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream().filter(
                                                vo -> vo.getCode().indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
                                                        || vo.getCode().endsWith(key.substring(key.length() - 3, key.length())))
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(Double.valueOf(vo.getWinbonus()) * multiple);
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
                //更新注单数据
                updateDataAll(sumList, noticeReq, list, bonusTime);
                countList.add(i);
            }).start();
            count++;
        }
        while (true) {
//			System.out.println("\n============== " + countList.size() + " / " + allBetList.size());
//			for (Integer i : countList) {
//				System.out.print(i+" ");
//			}
            if (countList.size() == allBetList.size()) {
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        doCongealToReal(noticeReq);

        Long endTime = System.currentTimeMillis();
        if(allBetList.size() > 0){
            log.info("\n============== notice - {} ================" +
                    "\nlotteryId = {}" +
                    "\nissue = {}" +
                    "\n注单数(3000):{}" +
                    "\n开始时间:{}" +
                    "\n结束时间:{}" +
                    "\n耗时:{}", noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), allBetList.size(), startTime, endTime, endTime - startTime);
        }
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
            while (true) {
                PageHelper.startPage(pageNo, pageSize);
                // TODO Auto-generated method stub
                // 获取对应奖期对应彩种未撤单且未派奖的所有订单
                List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
                if (list.isEmpty()) {
                    break;
                }
                pageNo += 1;
                List<BetInfoEntity> allWinList = Lists.newArrayList();
                List<Integer> endList = Lists.newArrayList();

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
                                    vo -> vo.getCode().indexOf(code) >= 0
//										"2D头尾"				"2D头"
                                            && (vo.getMethodCode().equals("2DTW") || vo.getMethodCode().equals("2DT"))).collect(Collectors.toList());
                            allWinList.addAll(collect);
                        }
                        List<BetInfoEntity> collect = betList.stream().filter(vo -> vo.getCode().indexOf(endCode) >= 0
//							"2D头尾"				"2D尾"
                                && (vo.getMethodCode().equals("2DTW") || vo.getMethodCode().equals("2DW"))).collect(Collectors.toList());
                        allWinList.addAll(collect);
                    } catch (Exception e) {
                        // TODO: handle exception
                        e.printStackTrace();
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
                            List<BetInfoEntity> collect = betList.stream().filter(vo -> vo.getCode().indexOf(code) >= 0
                                    //	"3D头尾"			"3D头"
                                    && (vo.getMethodCode().equals("3DTW") || vo.getMethodCode().equals("3DT"))).collect(Collectors.toList());
                            allWinList.addAll(collect);
                        }
                        List<BetInfoEntity> collect = betList.stream().filter(vo -> vo.getCode().indexOf(endCode) >= 0
                                //"3D头尾" 			"3D尾"
                                && (vo.getMethodCode().equals("3DTW") || vo.getMethodCode().equals("3DW"))).collect(Collectors.toList());
                        allWinList.addAll(collect);
                    } catch (Exception e) {
                        // TODO: handle exception
                        e.printStackTrace();
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
                        // TODO: handle exception
                        e.printStackTrace();
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
                                        .filter(vo -> vo.getCode().indexOf(key + ",") >= 0
                                                || vo.getCode().endsWith(key) && vo.getMethodCode().equals("2DBZ")) //"2d包组玩法"
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
                                                && vo.getMethodCode().equals("2DBZ"))    //"2d包组玩法"
                                                || (vo.getCode().endsWith(key.substring(key.length() - 2, key.length()))
                                                && vo.getMethodCode().equals("3DBZ")))    //"3d包组玩法"
                                        .collect(Collectors.toList());
                                winList.forEach(vo -> {
                                    vo.setBonus(vo.getBonus() * multiple);
                                });
                                allWinList.addAll(winList);
                            } else {
                                List<BetInfoEntity> winList = betList.stream()
                                        .filter(vo -> (vo.getCode()
                                                .indexOf(key.substring(key.length() - 2, key.length()) + ",") >= 0
                                                && vo.getMethodCode().equals("2DBZ"))    //"2d包组玩法"
                                                || (vo.getCode()
                                                .endsWith(key.substring(key.length() - 2, key.length()))
                                                && vo.getMethodCode().equals("2DBZ"))//"2d包组玩法"
                                                || (vo.getCode()
                                                .indexOf(key.substring(key.length() - 3, key.length()) + ",") >= 0
                                                && vo.getMethodCode().equals("3DBZ"))//"3d包组玩法"
                                                || (vo.getCode().endsWith(key.substring(key.length() - 3, key.length()))
                                                && vo.getMethodCode().equals("3DBZ"))//"3d包组玩法"
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
                    }
                    endList.add(4);
                }).start();

                new Thread(() -> {//pl2玩法
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
                    }
                    endList.add(5);
                }).start();

                new Thread(() -> {//pl3玩法
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
                updateDataAll(sumList, noticeReq, list, bonusTime);
                waitList.add(1);
            }

            while (true) {
                if (waitList.size() == (pageNo - 1)) {
                    break;
                }
                Thread.sleep(100);
            }

            doCongealToReal(noticeReq);
            Long endTime = System.currentTimeMillis();
            if (pageNo > 1) {
                log.info("\n============== noticeNorth - {} ================" +
                        "\nlotteryId = {}" +
                        "\nissue = {}" +
                        "\n注单数(3000):{}" +
                        "\n开始时间:{}" +
                        "\n结束时间:{}" +
                        "\n耗时:{}", noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), pageNo - 1, startTime, endTime, endTime - startTime);
            }
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
        while (true) {
            PageHelper.startPage(pageNo, pageSize);
            // TODO Auto-generated method stub
            // 获取对应奖期对应彩种未撤单且未派奖的所有订单
            List<BetInfoEntity> list = betInfoMapper.selectListByNoticeReq(noticeReq);
            if (list.isEmpty()) {
                break;
            }
            pageNo += 1;
            List<BetInfoEntity> allWinList = Lists.newArrayList();
            List<Integer> endList = Lists.newArrayList();
            //1D头、2D头、3D头、1D尾，2D尾
            new Thread(() -> {
                try {
                    List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("1DT") || vo.getMethodCode().equals("1DW")
                            || vo.getMethodCode().equals("2DT") || vo.getMethodCode().equals("2DW")
                            || vo.getMethodCode().equals("3DT")).collect(Collectors.toList());
                    String headCode = codeList.get(0).substring(3, 6);
                    String headCode1 = codeList.get(0).substring(4, 6);
                    String endCode = codeList.get(maxSize);

                    List<BetInfoEntity> collect = betList.stream().filter(vo -> (headCode.indexOf(vo.getCode()) >= 0
                                    && (vo.getMethodCode().equals("3DT") || vo.getMethodCode().equals("1DT"))) // "3D头"   "1D头"
                                    || (headCode1.indexOf(vo.getCode()) >= 0 && vo.getMethodCode().equals("2DT"))//"2D头"
                                    || (endCode.indexOf(vo.getCode()) >= 0 && (vo.getMethodCode().equals("2DW") || vo.getMethodCode().equals("1DW"))))
                            .collect(Collectors.toList());
                    allWinList.addAll(collect);
                } catch (Exception e) {
                    // TODO: handle exception
                }
                endList.add(1);
            }).start();

            //3D前三、3D后三
            new Thread(() -> {
                try {
                    List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("3DHS") || vo.getMethodCode().equals("3DQS")).collect(Collectors.toList());
                    String frontThreeCode = codeList.get(1) + codeList.get(2);
                    String afterThreeCode = codeList.get(3) + codeList.get(4);

                    List<BetInfoEntity> collect = betList.stream().filter(vo -> ((frontThreeCode.startsWith(vo.getCode()) || frontThreeCode.endsWith(vo.getCode())) && vo.getMethodCode().equals("3DQS")) //"3D前三"
                            || ((afterThreeCode.startsWith(vo.getCode()) || afterThreeCode.endsWith(vo.getCode())) && vo.getMethodCode().equals("3DHS"))).collect(Collectors.toList()); //"3D后三"
                    allWinList.addAll(collect);
                } catch (Exception e) {
                    // TODO: handle exception
                }
                endList.add(2);
            }).start();
            //2D头奖组选  3D头奖组选
            new Thread(() -> {
                try {
                    List<BetInfoEntity> betList = list.stream().filter(vo -> vo.getMethodCode().equals("2DTJZX") || vo.getMethodCode().equals("3DTJZX")).collect(Collectors.toList());
                    String headCode = codeList.get(0).substring(3, 6);

                    char[] a = headCode.toCharArray();
                    java.util.Arrays.sort(a);
                    String finalHeadCode = new String(a);
                    List<BetInfoEntity> collect = betList.stream().filter(vo -> {
                        char[] codeArr = vo.getCode().toCharArray();
                        java.util.Arrays.sort(codeArr);
                        String tjzxCode = new String(codeArr);
                        return (vo.getMethodCode().equals("3DTJZX") && finalHeadCode.equals(tjzxCode))
                                || vo.getMethodCode().equals("2DTJZX")
                                && (finalHeadCode.endsWith(tjzxCode)
                                || finalHeadCode.startsWith(tjzxCode)
                                || (finalHeadCode.substring(0, 1).equals(tjzxCode.substring(0, 1)) && finalHeadCode.substring(2, 3).equals(tjzxCode.substring(1, 2)))
                        );
                    }).collect(Collectors.toList());
                    allWinList.addAll(collect);
                } catch (Exception e) {
                    // TODO: handle exception
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
            //中奖订单
            List<BetInfoEntity> sumList = getSumList(allWinList);
            updateDataAll(sumList, noticeReq, list, bonusTime);
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
        doCongealToReal(noticeReq);
        Long endTime = System.currentTimeMillis();
        if (pageNo > 1) {
            log.info("\n============== noticeTh - {} ================" +
                    "\nlotteryId = {}" +
                    "\nissue = {}" +
                    "\n注单数(3000):{}" +
                    "\n开始时间:{}" +
                    "\n结束时间:{}" +
                    "\n耗时:{}", noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), pageNo - 1, startTime, endTime, endTime - startTime);
        }
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
        String endCode = codeList.get(1);
        while (true) {
            PageHelper.startPage(pageNo, pageSize);
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
            List<BetInfoEntity> collect = list.stream().filter(vo -> (headCode.indexOf(vo.getCode()) >= 0 && ("1DT".equals(vo.getMethodCode()) || "3DT".equals(vo.getMethodCode())))
                    || (endCode.indexOf(vo.getCode()) >= 0 && ("1DW".equals(vo.getMethodCode()) || "2DW".equals(vo.getMethodCode())))
                    || (headCode.substring(1, 3).indexOf(vo.getCode()) >= 0 && "2DT".equals(vo.getMethodCode()))).collect(Collectors.toList());
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
            updateDataAll(sumList, noticeReq, list, bonusTime);
        }
        doCongealToReal(noticeReq);

        Long endTime = System.currentTimeMillis();
        if (pageNo > 1) {
            log.info("\n============== noticeLw - {} ================" +
                    "\nlotteryId = {}" +
                    "\nissue = {}" +
                    "\n注单数(3000):{}" +
                    "\n开始时间:{}" +
                    "\n结束时间:{}" +
                    "\n耗时:{}", noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), pageNo - 1, startTime, endTime, endTime - startTime);
        }
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

                return ("STH".equals(vo.getMethodCode()) && vo.getCode().contains(sortCode)) //三同号
                        || ("SBTH".equals(vo.getMethodCode()) && codeListOnly.size() == 3 && (vo.getCode().contains(codeList.get(0)) && vo.getCode().contains(codeList.get(1)) && vo.getCode().contains(codeList.get(2)))) //三不同号
                        || ("DX".equals(vo.getMethodCode()) && vo.getCode().contains(sortCode)) //二同号-单选
                        || ("FX".equals(vo.getMethodCode()) && (vo.getCode().contains(removeFirst) || vo.getCode().contains(removeLast))); //二同号-复选
            }).collect(Collectors.toList());
            List<BetInfoEntity> sumList = getSumList(allWinList);
            updateDataAll(sumList, noticeReq, list, bonusTime);
        }
        doCongealToReal(noticeReq);

        Long endTime = System.currentTimeMillis();
        if (pageNo > 1) {
            log.info("\n============== noticeKs - {} ================" +
                    "\nlotteryId = {}" +
                    "\nissue = {}" +
                    "\n注单数(3000):{}" +
                    "\n开始时间:{}" +
                    "\n结束时间:{}" +
                    "\n耗时:{}", noticeReq.getTitle(), noticeReq.getLotteryId(), noticeReq.getIssue(), pageNo - 1, startTime, endTime, endTime - startTime);
        }
    }

    /**
     * 相同投注订单计算中奖总金额
     *
     * @param allWinList
     * @return
     * @author yangxy
     * @version 创建时间：2025年12月30日 下午7:26:09
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

    public void newUpdateAll(List<BetInfoEntity> sumList, NoticeReq noticeReq, List<BetInfoEntity> list, Date bonusTime) {
        //中奖订单ID列表
        List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
        //未中奖订单ID
        List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
                .collect(Collectors.toList());
        if (!notWinList.isEmpty()) {
            List<BetInfoEntity> tempList = new ArrayList<>();
            tempList.addAll(notWinList);
            tempList.forEach(vo -> {
                vo.setIsGetprize(2);
                vo.setPrizeStatus(1);
                vo.setUpdatedAt(new Date());
            });
            List<String> setCloumList = Lists.newArrayList("isGetprize", "prizeStatus", "updatedAt");
            List<String> whereValueList = Lists.newArrayList("projectId");
            jdbcCreateSqlUtil.batchUpdate(tempList,
                    "update " + noticeReq.getTitle() + "_projects "
                            + "set is_getprize = ?,prize_status = ?,updated_at = ? " +
                            "where project_id = ?",
                    setCloumList, whereValueList);
        }
        if (!sumList.isEmpty()) {
            List<BetInfoEntity> tempList = new ArrayList<>();
            tempList.addAll(sumList);
            tempList.forEach(vo -> {
                vo.setBonus(Double.valueOf(vo.getWinbonus()));
                vo.setIsGetprize(1);
                vo.setBonusTime(new Date());
                vo.setUpdateTime(new Date());
                vo.setUpdatedAt(new Date());
            });
            List<String> setCloumList = Lists.newArrayList("bonus", "isGetprize", "bonusTime", "updateTime", "updatedAt");
            List<String> whereValueList = Lists.newArrayList("projectId");
            jdbcCreateSqlUtil.batchUpdate(tempList,
                    "update " + noticeReq.getTitle() + "_projects set "
                            + "bonus = ?, is_getprize = ?, bonus_time = ?, update_time = ? updated_at = ? " +
                            "where project_id = ? and is_cancel = 0",
                    setCloumList, whereValueList);
        }
        List<String> userIds = sumList.stream().map(BetInfoEntity::getUserId).collect(Collectors.toSet()).stream().collect(Collectors.toList());
        //删除临时注单记录 1
        List<String> projectIds = list.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
        projectsTmpMapper.deleteBatchIds(projectIds);

        //中奖
        if (userIds == null || userIds.size() == 0) {
            return;
        }
        //锁定用户资金
//        betInfoMapper.doLockUserFund(noticeReq.getTitle(), userIds, 5);
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
            //删除临时注单记录 1
            List<String> projectIds = list.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
            projectsTmpMapper.deleteBatchIds(projectIds);
            String title = noticeReq.getTitle();
            //中奖订单-新增order 5 并加钱
            ordersToolService.getOrdersListAll(sumList, noticeReq.getTitle(), 5, noticeReq.getRoomMaster());
            List<String> winIdList = sumList.stream().map(BetInfoEntity::getProjectId).collect(Collectors.toList());
            //未中奖订单ID
            List<BetInfoEntity> notWinList = list.stream().filter(vo -> !winIdList.contains(vo.getProjectId()))
                    .collect(Collectors.toList());
            //批量修改未中奖订单
            if (!notWinList.isEmpty()) {
                betInfoMapper.updateIsGetprize2(notWinList, title);
            }

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

    /**
     * 结算
     *
     * @param noticeReq
     */
    public void doCongealToReal(NoticeReq noticeReq) {
        new Thread(() -> {
            //结算
            ManualDistributionReq condition = new ManualDistributionReq();
            condition.setIssue(noticeReq.getIssue());
            condition.setLotteryId(noticeReq.getLotteryId());
            condition.setMasterId(String.valueOf(noticeReq.getRoomMaster().getMasterId()));
            oPissueToolService.doCongealToReal(condition);
        }).start();
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

}
