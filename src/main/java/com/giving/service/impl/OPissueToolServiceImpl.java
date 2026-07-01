package com.giving.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.ObjectUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.github.pagehelper.PageHelper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.enums.RedisKeyEnums;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.mapper.TempIssueInfoMapper;
import com.giving.req.ListIssueReq;
import com.giving.req.ManualDistributionReq;
import com.giving.service.AwardingProcessService;
import com.giving.service.BillOtherService;
import com.giving.service.OPissueToolService;
import com.giving.service.OrdersToolService;
import com.giving.util.RedisUtils;

import lombok.extern.slf4j.Slf4j;


/**
 * 开奖测试工具
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:11
 */
@Slf4j
@Service
public class OPissueToolServiceImpl implements OPissueToolService {
    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private AwardingProcessService awardingProcessService;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;
    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private BillOtherService billOtherService;
    @Autowired
    private OrdersToolService ordersToolService;
    @Autowired
    private RedisUtils redisUtils;
    /**
     * @param req
     */
    @Override
    public ApiResp<String> resteDrawSource(ListIssueReq req) {
        //判断是否存在奖期
        LambdaQueryWrapper<IssueInfoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IssueInfoEntity::getLotteryId, req.getLotteryId());
        queryWrapper.eq(IssueInfoEntity::getIssue, req.getIssue());
        IssueInfoEntity issueInfo = issueInfoMapper.selectOne(queryWrapper);
        List<RoomMasterEntity> roomMasters = roomMasterMapper.selectTitle();
        for (RoomMasterEntity roomMaster : roomMasters) {
            awardingProcessService.lotteryDraw(roomMaster, issueInfo);
        }
        return ApiResp.sucess();
    }

    @Override
    public ApiResp<String> manualDistribution(ManualDistributionReq req) {
        LambdaQueryWrapper<IssueInfoEntity> wrapper = new LambdaQueryWrapper<IssueInfoEntity>();
        wrapper.eq(IssueInfoEntity::getLotteryId, req.getLotteryId());
        wrapper.eq(IssueInfoEntity::getIssue, req.getIssue());
        IssueInfoEntity issueInfoEntity = issueInfoMapper.selectOne(wrapper);

        if (ObjectUtils.isEmpty(issueInfoEntity)) {
            log.info("manualDistribution main issue not exists, lotteryId={}, issue={}",
                    req.getLotteryId(), req.getIssue());
            return ApiResp.paramError("issue not exists");
        }
        
        if (StringUtils.isEmpty(issueInfoEntity.getCode())) {
            log.info("========未录号===========");
            return ApiResp.paramError("未录号");
        }

        if (req.getMasterId() != null) {
            LambdaQueryWrapper<RoomMasterEntity> wrapper1 = new LambdaQueryWrapper<>();
            wrapper1.eq(RoomMasterEntity::getMasterId, req.getMasterId());
            RoomMasterEntity roomMasterEntity = roomMasterMapper.selectOne(wrapper1);
            if (ObjectUtils.isEmpty(roomMasterEntity)) {
                //return 厅组id错误，厅组不存在
                log.info("厅组id错误，厅组不存在");
                return ApiResp.paramError("厅组id错误，厅组不存在");
            }

            IssueInfoEntity issueInfo = issueInfoMapper.selectByTitle(roomMasterEntity.getTitle(), req);
            
            if(ObjectUtils.isEmpty(issueInfo)) {
                Integer pendingProjectCount = betInfoMapper.countPendingManualDistributionProjects(
                        roomMasterEntity.getTitle(), req.getLotteryId(), req.getIssue());
                if (pendingProjectCount == null || pendingProjectCount <= 0) {
                    log.info("manualDistribution room issue not exists and no pending award project, skip award, title={}, masterId={}, lotteryId={}, issue={}",
                            roomMasterEntity.getTitle(), req.getMasterId(), req.getLotteryId(), req.getIssue());
                    return ApiResp.sucess();
                }

                log.warn("manualDistribution room issue not exists but pending award projects found, recreate room issue and continue award, title={}, masterId={}, lotteryId={}, issue={}, pendingCount={}",
                        roomMasterEntity.getTitle(), req.getMasterId(), req.getLotteryId(), req.getIssue(), pendingProjectCount);
                issueInfoMapper.insertIssueToRoomIfAbsent(roomMasterEntity.getTitle(), issueInfoEntity);
                List<String> titles = new ArrayList<>();
                titles.add(roomMasterEntity.getTitle());
                issueInfoMapper.insertIssueToRooms(titles, issueInfoEntity);
                issueInfo = issueInfoMapper.selectByTitle(roomMasterEntity.getTitle(), req);
                if (ObjectUtils.isEmpty(issueInfo)) {
                    log.error("manualDistribution recreate room issue failed, title={}, masterId={}, lotteryId={}, issue={}",
                            roomMasterEntity.getTitle(), req.getMasterId(), req.getLotteryId(), req.getIssue());
                    return ApiResp.paramError("room issue recreate failed");
                }
            }

            if (StringUtils.isEmpty(issueInfo.getCode())) {
                log.info("厅组id错误，厅组未录号");
                List<String> titles = new ArrayList<>();
                titles.add(roomMasterEntity.getTitle());
                issueInfoMapper.insertIssueToRooms(titles, issueInfoEntity);
                issueInfo.setCode(issueInfoEntity.getCode());
            } else {
                log.info("厅组id错误，厅组已录号");
//                return ApiResp.paramError("厅组id错误，厅组已录号");
            }
            awardingProcessService.lotteryDraw(roomMasterEntity, issueInfo);
        }

        return ApiResp.sucess();

    }

    @Override
    public ApiResp<String> doCongealToReal(ManualDistributionReq req) {
        Long startTime = System.currentTimeMillis();
        try {
            LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoomMasterEntity::getMasterId, req.getMasterId());
            RoomMasterEntity roomMasterEntity = roomMasterMapper.selectOne(wrapper);
            if (ObjectUtils.isEmpty(roomMasterEntity)) {
                throw new RuntimeException("[厅主不存在] 厅主ID:" + req.getMasterId());
            }
            TempIssueInfoEntity issueInfo = tempIssueInfoMapper.selectByTitle(roomMasterEntity.getTitle(),
                    req.getLotteryId(),
                    req.getIssue());
            boolean issueInfoExists = !ObjectUtils.isEmpty(issueInfo);
            if (!issueInfoExists) {
                issueInfo = new TempIssueInfoEntity();
                issueInfo.setLotteryId(req.getLotteryId());
                issueInfo.setIssue(req.getIssue());
                PageHelper.startPage(1, 1);
                List<BetInfoEntity> pendingProjects = betInfoMapper.checkProjects(
                        roomMasterEntity.getTitle(), issueInfo);
                if (pendingProjects != null && !pendingProjects.isEmpty()) {
                    log.warn("厅主奖期不存在但发现待结算订单，继续按订单实际状态结算。厅主ID={}，彩种ID={}，奖期={}，首笔订单ID={}",
                            req.getMasterId(), req.getLotteryId(), req.getIssue(),
                            pendingProjects.get(0).getProjectId());
                } else {
                    log.info("厅主奖期不存在且没有待结算订单，本期无投注，跳过结算。厅主ID:{}，彩种ID:{}，奖期:{}",
                            req.getMasterId(), req.getLotteryId(), req.getIssue());
                    return ApiResp.sucess();
                }
            }
            int pageSize = 200;
            int batchCount = 0;
            boolean deductStatusStarted = Integer.valueOf(1).equals(issueInfo.getStatusDeduct());
            // 获取所有尚未'真实扣款'的方案
            while (true) {
                // 每批处理后待结算集合会缩小，必须始终取第一页，避免 offset 跳过订单。
                PageHelper.startPage(1, pageSize);
                //获取所有尚未'真实扣款'的方案
                List<BetInfoEntity> projects = betInfoMapper.checkProjects(roomMasterEntity.getTitle(), issueInfo);
                if (projects == null || projects.isEmpty()) {
                    issueInfo.setStatusDeduct(2);  //无方案真实扣款结束
                    break;
                }
                if (!deductStatusStarted) {
                    if (Integer.valueOf(2).equals(issueInfo.getStatusDeduct())) {
                        log.warn("奖期已标记结算完成但仍存在待结算订单，自动恢复结算。厅主ID={}，彩种ID={}，奖期={}，本批订单数={}",
                                req.getMasterId(), req.getLotteryId(), req.getIssue(), projects.size());
                    }
                    issueInfo.setStatusDeduct(1);
                    if (issueInfoExists) {
                        ordersToolService.updateIssueDeduct(issueInfo, roomMasterEntity.getTitle());
                    }
                    deductStatusStarted = true;
                }
                int retryCount = 0;
                while (true) {
                    try {
                        //执行type 8 真实扣款---结算
                        Boolean success = ordersToolService.getOrdersListAll(
                                projects, roomMasterEntity.getTitle(), 8, roomMasterEntity);
                        if (!Boolean.TRUE.equals(success)) {
                            String message = "部分用户钱包锁获取失败，相关订单的钱包和结算状态保持不变，请稍后重试";
                            log.warn("结算暂停，奖期={}，彩种ID={}，厅主ID={}，原因={}",
                                    req.getIssue(), req.getLotteryId(), req.getMasterId(), message);
                            return ApiResp.paramError(message);
                        }
                        break;
                    } catch (RuntimeException e) {
                        retryCount++;
                        if (retryCount >= 3) {
                            throw new RuntimeException("结算批次" + (batchCount + 1)
                                    + "连续失败3次，具体原因：" + getRootCauseMessage(e), e);
                        }
                        log.warn("结算批次{}执行失败，准备第{}次重试，原因：{}",
                                batchCount + 1, retryCount + 1, getRootCauseMessage(e));
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("结算重试等待被中断", interruptedException);
                        }
                    }
                }
                batchCount++;
            }
            //修改 真实扣款状态
            if (issueInfoExists) {
                ordersToolService.updateIssueDeduct(issueInfo, roomMasterEntity.getTitle());
            } else {
                log.warn("待结算订单已处理完成，但厅主奖期记录仍不存在，无法写入结算完成状态。厅主ID={}，彩种ID={}，奖期={}",
                        req.getMasterId(), req.getLotteryId(), req.getIssue());
            }

            Long endTime = System.currentTimeMillis();
            log.info("\n====结算进程 - {} - {} - {}" +
                    "\n结算批次:{}" +
                    "\n开始时间:{}" +
                    "\n结束时间:{}" +
                    "\n耗时:{}", req.getIssue(), req.getLotteryId(), req.getMasterId(),
                    batchCount, startTime, endTime, endTime - startTime);

            //设置当前平台盈亏

//            new Thread(this::setPlatformThreshold).start();
            return ApiResp.sucess();
        } catch (RuntimeException e) {
            log.error("结算失败，奖期={}，彩种ID={}，厅主ID={}",
                    req.getIssue(), req.getLotteryId(), req.getMasterId(), e);
            return ApiResp.paramError(e.getMessage());
        }
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        return message == null || message.trim().isEmpty()
                ? rootCause.getClass().getSimpleName()
                : message;
    }

    /**
     * 设置平台盈亏
     *
     */
    public void setPlatformThreshold() {
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String nowString = sdf.format(new Date());
            String priceValue = redisUtils.rawHget(RedisKeyEnums.C_PROFIT_DATA.key, nowString+"_price");
            String bonusValue = redisUtils.rawHget(RedisKeyEnums.C_PROFIT_DATA.key, nowString+"_bonus");
            if(priceValue == null || bonusValue == null){
                return;
            }
            //（总投注-总派奖+总反点）/总投注
            BigDecimal price = new BigDecimal(priceValue);
            BigDecimal bonus = new BigDecimal(bonusValue);
            BigDecimal t = (price.subtract(bonus)).divide(price,2);
            log.info("\n平台盈亏:( {} - {} ) / {} = {}",price,bonus,price,t);
            //设置当前平台盈亏
            billOtherService.nowThreshold(t.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public ApiResp<String> doRabate(ManualDistributionReq req) {
        try{
            LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoomMasterEntity::getMasterId, req.getMasterId());
            RoomMasterEntity roomMasterEntity = roomMasterMapper.selectOne(wrapper);
            if (ObjectUtils.isEmpty(roomMasterEntity)) {
                throw new RuntimeException("[厅主不存在] 厅主ID:" + req.getMasterId());
            }
            TempIssueInfoEntity issueInfo = tempIssueInfoMapper.selectByTitle(roomMasterEntity.getTitle(),
                   req.getLotteryId(),
                    req.getIssue());
            if (ObjectUtils.isEmpty(issueInfo)) {
                throw new RuntimeException("[厅主奖期不存在] 厅主ID:" + req.getMasterId() + "奖期：" + req.getIssue());
            }
            if (issueInfo.getStatusUserPoint() == 2) {
                throw new RuntimeException("派發返點已完成 status_user_point=2");
            } else if (issueInfo.getStatusUserPoint() == 0) {
                //修改為派發返點進行中
                issueInfo.setStatusUserPoint(1);
                if (tempIssueInfoMapper.updateByTitleStatusPoint(roomMasterEntity.getTitle(), issueInfo) != 1) {
                    throw new RuntimeException("修改為派發返點進行中失败");
                }
            }
            List<BetInfoEntity> projects = betInfoMapper.checkProjectsPoint(roomMasterEntity.getTitle(), issueInfo);

            if (!ordersToolService.getOrdersListAll(projects, roomMasterEntity.getTitle(), 4, roomMasterEntity)) {
                throw new RuntimeException("新增账变失败");
            }



            issueInfo.setStatusUserPoint(2);
            if (tempIssueInfoMapper.updateByTitleStatusPoint(roomMasterEntity.getTitle(), issueInfo) != 1) {
                throw new RuntimeException("修改為派發返點進行中失败");
            }
            return ApiResp.sucess();
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ApiResp.paramError(e.getMessage());
        }
    }

    @Override
    public ApiResp<String> doForceCongealToReal(ManualDistributionReq req) {
        try {
            List<RoomMasterEntity> roomMasterList = new ArrayList<>();
            if (req.getMasterId().equals("0")) {
                roomMasterList.addAll(roomMasterMapper.selectTitle());
            } else {
                LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(RoomMasterEntity::getMasterId, req.getMasterId());
                roomMasterList.add(roomMasterMapper.selectOne(wrapper));
            }
            List<Integer> waitList = java.util.Collections.synchronizedList(new ArrayList<>());
            List<String> errorMessageList = java.util.Collections.synchronizedList(new ArrayList<>());
            for (RoomMasterEntity roomMaster : roomMasterList) {
                new Thread(() -> {
                    TempIssueInfoEntity issueInfo = tempIssueInfoMapper.selectByTitle(roomMaster.getTitle(), req.getLotteryId(), req.getIssue());
                    if (ObjectUtils.isEmpty(issueInfo)) {
                        waitList.add(1);
                        return;
                    }
                    int pageSize = 1000;
                    // 获取所有尚未'真实扣款'的方案
                    while (true) {
                        PageHelper.startPage(1, pageSize);
                        List<BetInfoEntity> projects = betInfoMapper.checkProjects(roomMaster.getTitle(), issueInfo);
                        //如果获取的结果集为空, 则表示当前奖期已全部'真实扣款'完成. 更新状态值
                        if (ObjectUtils.isEmpty(projects) || projects == null) {
                            break;
                        }
                        if (!ordersToolService.getOrdersListAll(projects, roomMaster.getTitle(), 8, roomMaster)) {
                            errorMessageList.add("新增结算账变失败，厅主ID：" + roomMaster.getMasterId());
                            break;
                        }
                    }
                    waitList.add(1);
                }).start();
            }
            while (true) {
                if (waitList.size() == roomMasterList.size()) {
                    break;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (errorMessageList.size() > 0) {
                return ApiResp.paramError(errorMessageList.toString());
            }
            return ApiResp.sucess();
        } catch (Exception e) {
            return ApiResp.paramError(e.getMessage());
        }
    }
}
