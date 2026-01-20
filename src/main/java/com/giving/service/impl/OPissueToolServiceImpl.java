package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.*;
import com.giving.mapper.*;
import com.giving.req.ManualDistributionReq;
import com.giving.service.AwardingProcessService;
import com.giving.service.OPissueToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:11
 */
@Service
@Transactional
public class OPissueToolServiceImpl implements OPissueToolService {
    private static final Logger log = LoggerFactory.getLogger(AwardingProcessServiceImpl.class);
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
    private UserFundMapper userFundMapper;
    @Autowired
    private OrdersMapper ordersMapper;

    //public AwardingProcessServiceImpl awardingProcess = new AwardingProcessServiceImpl();

    @Override
    public ApiResp<String> manualDistribution(ManualDistributionReq req) {
        LambdaQueryWrapper<IssueInfoEntity> wrapper = new LambdaQueryWrapper<IssueInfoEntity>();
        wrapper.eq(IssueInfoEntity::getLotteryId, req.getLotteryId());
        wrapper.eq(IssueInfoEntity::getIssue, req.getIssue());
        IssueInfoEntity issueInfoEntity = issueInfoMapper.selectOne(wrapper);
        if (issueInfoEntity.getCode() == null || issueInfoEntity.getCode().equals("")) {
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
            if (issueInfo.getCode() == null || issueInfo.getCode().equals("")) {
                log.info("厅组id错误，厅组未录号");
                List<String> titles = new ArrayList<>();
                titles.add(roomMasterEntity.getTitle());
                issueInfoMapper.insertIssueToRooms(titles, issueInfoEntity);
                issueInfo.setCode(issueInfoEntity.getCode());
            } else {
                log.info("厅组id错误，厅组已录号");
                return ApiResp.paramError("厅组id错误，厅组已录号");
            }
            awardingProcessService.lotteryDraw(roomMasterEntity, issueInfo);
        }

        return ApiResp.sucess();

    }

    @Override
    public ApiResp<String> doCongealToReal(ManualDistributionReq req) {
        try {
            LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoomMasterEntity::getMasterId, req.getMasterId());
            RoomMasterEntity roomMasterEntity = roomMasterMapper.selectOne(wrapper);
            if (ObjectUtils.isEmpty(roomMasterEntity)) {
                throw new RuntimeException("[厅主不存在] 厅主ID:" + req.getMasterId());
            }
            TempIssueInfoEntity issueInfo = tempIssueInfoMapper.selectByTitle(roomMasterEntity.getTitle(),
                    req.getLotteryId().toString(),
                    req.getIssue());
            if (ObjectUtils.isEmpty(issueInfo)) {
                throw new RuntimeException("[厅主奖期不存在] 厅主ID:" + req.getMasterId() + "奖期：" + req.getIssue());
            }
            if (issueInfo.getStatusDeduct() == 2) {
                throw new RuntimeException("真實扣款已完成 status_deduct=2");
            }
            //修改為真實扣款進行中
            issueInfo.setStatusDeduct(1);
            if (tempIssueInfoMapper.updateByTitleStatusDeduct(roomMasterEntity.getTitle(), issueInfo) != 1) {
                throw new RuntimeException("修改奖期为真实扣款中失败");
            }
            // 获取所有尚未'真实扣款'的方案
            List<BetInfoEntity> projects = betInfoMapper.checkProjects(roomMasterEntity.getTitle(), issueInfo);
            //如果获取的结果集为空, 则表示当前奖期已全部'真实扣款'完成. 更新状态值
            if (ObjectUtils.isEmpty(projects)) {
                issueInfo.setStatusDeduct(2);
                if (tempIssueInfoMapper.updateByTitleStatusDeduct(roomMasterEntity.getTitle(), issueInfo) != 1) {
                    throw new RuntimeException("修改奖期为真实扣款完成失败");
                }
            }

//            List<String> userIds = projects.stream().map(BetInfoEntity::getUserId).collect(Collectors.toList());
            for (BetInfoEntity project : projects) {
                //锁定用户资金 -- 上鎖
                this.doLockUserFund(project.getUserId(), true, 4, "CR_001",roomMasterEntity.getTitle());
                try {
                    //添加账变
                    if (!this.addOrdersList(project.getUserId(), project, 4, roomMasterEntity.getTitle())){
                        throw new RuntimeException("新增账变异常");
                    }
                    //解锁
                    this.doLockUserFund(project.getUserId(), false, 4, "CR_004",roomMasterEntity.getTitle());
                } catch (Exception e) {
                    this.doLockUserFund(project.getUserId(), false, 4, "CR_004",roomMasterEntity.getTitle());
                    throw new RuntimeException(e);
                }
            }
            //真實扣款 - 最後確認
//            List<BetInfoEntity> projects2 = betInfoMapper.checkProjects(roomMasterEntity.getTitle(), issueInfo);
//            if (ObjectUtils.isEmpty(projects2)) {
            issueInfo.setStatusDeduct(2);
            if (tempIssueInfoMapper.updateByTitleStatusDeduct(roomMasterEntity.getTitle(), issueInfo) != 1) {
                throw new RuntimeException("修改奖期为真实扣款完成失败");
            }
//            }
            return ApiResp.sucess();
        } catch (RuntimeException e) {
            return ApiResp.paramError(e.getMessage());
        }
    }

    public Boolean doLockUserFund(String userId, Boolean bIsLocked, Integer sWalletType, String lockAction,String title) {
        try {

            // TRUE : 上鎖 ； FALSE : 解鎖
            // 钱包类别
            // 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款, 5: 单注验派
            int sNowIsLock = (bIsLocked) ? 0 : 1;
            int sToIsLock = (bIsLocked) ? 1 : 0;
            int iWalletType = (int) sWalletType;
            int iAffectNumber = (iWalletType == 0) ? 6 : 1;
            String sLockAction = lockAction + ((bIsLocked) ? " 上锁" : " 解锁");// TRUE : 上鎖 ; FALSE : 解鎖

            LambdaQueryWrapper<UserFundEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserFundEntity::getUserid, userId);
            wrapper.eq(UserFundEntity::getIslocked, sNowIsLock);
            wrapper.eq(UserFundEntity::getWalletType, sWalletType);
            List<UserFundEntity> userFundS;
            if (iWalletType > 0) {
                userFundS = userFundMapper.selectByUserAndTypeLock(title,userId,sNowIsLock,sWalletType);
            }else {
                userFundS = userFundMapper.selectByUserAndTypeAll(title,userId,sNowIsLock);
            }


            int updateCount = 0;
            for (UserFundEntity userFund : userFundS) {
                userFund.setIslocked(sToIsLock);
                userFund.setLockAction(sLockAction);
                updateCount += userFundMapper.updateLockedById(title,userFund); // 每次返回 0/1
            }
            if (!(updateCount >= iAffectNumber) && bIsLocked) {
                throw new Exception("失敗");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Boolean addOrdersList(String userId, BetInfoEntity project, int sWalletType, String title) {
        try {
            UserFundEntity userFund = userFundMapper.selectByUserAndType(title,sWalletType,userId);
            if (ObjectUtils.isEmpty(userFund)) {
                throw new Exception("错误未查询到用户钱包");
            }
            System.out.println("查询到钱包");

            Date date = new Date();
            BigDecimal preChannelBalance = userFund.getChannelbalance();
            BigDecimal preAvailableBalance = userFund.getAvailablebalance();
            BigDecimal preHoldBalance = userFund.getHoldbalance();
            BigDecimal amount = BigDecimal.valueOf(project.getTotalPrice());

            BigDecimal channelBalance = preChannelBalance.subtract(amount);
            BigDecimal holdBalance = preHoldBalance.subtract(amount);

            OrdersEntity order = new OrdersEntity();
            order.setEntry(uniqId().substring(0,10));
            order.setLotteryId(project.getLotteryId());
            order.setMethodId(project.getMethodId());
            order.setTaskId(project.getTaskId());
            order.setProjectId(project.getProjectId());
            order.setFromuserId(project.getUserId());
            order.setOrderTypeId(8);
            order.setTitle("游戏扣款");
            order.setAmount(amount);
            order.setDescription("游戏扣款");
            order.setPreBalance(preChannelBalance);
            order.setPreAvailable(preAvailableBalance);
            order.setPreHold(preHoldBalance);
            order.setChannelBalance(channelBalance);
            order.setAvailableBalance(preAvailableBalance);
            order.setHoldBalance(holdBalance);
            order.setIssue(project.getIssue());
            order.setModes(project.getModes());
            order.setCreatedAt(date);
            order.setUpdatedAt(date);
            order.setActionTime(date);

            if (ordersMapper.addOrdersList(order, title) <= 0) {
                throw new IllegalStateException("新增Orders 资料失败");
            }
            BetInfoEntity updateProject = new BetInfoEntity();
            updateProject.setIsDeduct(1);
            updateProject.setDeductTime(date);
            updateProject.setUpdateTime(date);
            updateProject.setUpdatedAt(date);
            betInfoMapper.updateDeduct(updateProject, title);

            UserFundEntity updateFund = new UserFundEntity();
            updateFund.setChannelbalance(channelBalance);
            updateFund.setAvailablebalance(preAvailableBalance);
            updateFund.setHoldbalance(holdBalance);
            updateFund.setUpdatedAt(date);
            userFundMapper.updateAddOrdersList(updateFund, userId, sWalletType, 1, title);

            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return false;
        }
    }
    static String uniqId() {
        // 类似 uniqid：时间 + 随机
        return Long.toHexString(System.nanoTime()) + Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
