package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.UserFundMapper;
import com.giving.service.UserFundLockTxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserFundLockTxServiceImpl implements UserFundLockTxService {
    @Resource
    private UserFundMapper userFundMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private BetInfoMapper betInfoMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Boolean doLockUserFund(String userId, Boolean bIsLocked, Integer sWalletType, String lockAction, String title) {
        try {
            // TRUE : 上鎖 ； FALSE : 解鎖
            // 钱包类别
            // 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款, 5: 单注验派
            int sNowIsLock = (bIsLocked) ? 0 : 1;
            int sToIsLock = (bIsLocked) ? 1 : 0;
            int iAffectNumber = (sWalletType == 0) ? 6 : 1;
            String sLockAction = lockAction + ((bIsLocked) ? " 上锁" : " 解锁");// TRUE : 上鎖 ; FALSE : 解鎖

            int updateCount = 0;

            /*//记录查询条件
            UserFundEntity data = new UserFundEntity();
            data.setUserid(userId);
            data.setIslocked(sNowIsLock);
            data.setWalletType(sWalletType);
            //php先查再改
            List<UserFundEntity> userFundS;
            if (sWalletType > 0) {
                //按照type查询到一条
                userFundS = userFundMapper.selectByUserAndTypeOne(title,data);
            }else {
                //查询到该用户全钱包
                userFundS = userFundMapper.selectByUserAndType(title,data);
            }

            //实际执行-锁定|解锁
            for (UserFundEntity userFund : userFundS) {
                userFund.setIslocked(sToIsLock);
                userFund.setLockAction(sLockAction);
                updateCount += userFundMapper.updateLockedById(title,userFund); // 每次返回 0/1
            }*/

            UserFundEntity userFund = new UserFundEntity();
            userFund.setUserid(userId);
            userFund.setIslocked(sToIsLock);
            userFund.setLockAction(sLockAction);
            if (sWalletType > 0) {
                userFund.setWalletType(sWalletType);
                updateCount += userFundMapper.updateLockedById(title,userFund); // 每次返回 0/1
            }else {
                for (int i = 0; i < 6; i++) {
                    userFund.setWalletType(i);
                    updateCount += userFundMapper.updateLockedById(title,userFund); // 每次返回 0/1
                }
            }

            if (!(updateCount >= iAffectNumber) && bIsLocked) {
                throw new RuntimeException("锁定/解锁用户资金失败 userId=" + userId);
            }
            return true;
        } catch (Exception e) {
            //手动标记回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Boolean addOrdersList(String userId, BetInfoEntity project, String title) {
        try {
            UserFundEntity userFundSum = userFundMapper.selectByUserSum(title, userId);
            if (ObjectUtils.isEmpty(userFundSum)) {
                throw new Exception("错误未查询到用户钱包");
            }
            System.out.println("查询到钱包");

            Date date = new Date();
            BigDecimal preChannelBalance = userFundSum.getChannelbalance();
            BigDecimal preAvailableBalance = userFundSum.getAvailablebalance();
            BigDecimal preHoldBalance = userFundSum.getHoldbalance();
            BigDecimal amount = BigDecimal.valueOf(project.getTotalPrice());

            BigDecimal channelBalance = preChannelBalance.subtract(amount);
            BigDecimal holdBalance = preHoldBalance.subtract(amount);

            OrdersEntity order = new OrdersEntity();
            String uuid = uniqId().substring(0, 16);
            System.out.println(uuid);
            order.setEntry(uuid);
            order.setLotteryId(project.getLotteryId());
            order.setMethodId(project.getMethodId());
            order.setTaskId(project.getTaskId());
            order.setProjectId(project.getProjectId());
            order.setFromuserId(project.getUserId());
            order.setOrderTypeId(8);
            order.setIssue(project.getIssue());
            order.setTitle("游戏扣款");
            order.setAmount(amount);
            order.setDescription("游戏扣款");
            order.setPreBalance(preChannelBalance);
            order.setPreAvailable(preAvailableBalance);
            order.setPreHold(preHoldBalance);
            order.setChannelBalance(channelBalance);
            order.setAvailableBalance(preAvailableBalance);
            order.setHoldBalance(holdBalance);
            order.setUniqueKey(String.valueOf(System.currentTimeMillis()));
            order.setModes(project.getModes());
            order.setCreatedAt(date);
            order.setUpdatedAt(date);
            order.setActionTime(date);

            if (ordersMapper.addOrdersList(order, title) <= 0) {
                throw new IllegalStateException("新增Orders 资料失败");
            }
            BetInfoEntity updateProject = new BetInfoEntity();
            updateProject.setProjectId(project.getProjectId());
            updateProject.setIsDeduct(1);
            updateProject.setDeductTime(date);
            updateProject.setUpdateTime(date);
            updateProject.setUpdatedAt(date);
            betInfoMapper.updateDeduct(updateProject, title);

            //修改钱包
            UserFundEntity o = new UserFundEntity();
            o.setUserid(project.getUserId());
            o.setWalletType(4);
            UserFundEntity os = userFundMapper.selectByUserAndTypeOne(title, o);

            if (ObjectUtils.isEmpty(os)) {
                throw new Exception("用户钱包不存在");
            }
            os.setChannelbalance(os.getChannelbalance().subtract(amount));//amount
            os.setHoldbalance(os.getHoldbalance().subtract(amount));
            os.setUpdatedAt(date);
            userFundMapper.updateAddOrdersList(os, title);
            return true;
        } catch (Exception e) {
            //手动标记回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;
        }
    }

    static String uniqId() {
        // 类似 uniqid：时间 + 随机
        return Long.toHexString(System.nanoTime()) + Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
