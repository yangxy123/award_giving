package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.RoomMasterMapper;
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
    @Autowired
    private RoomMasterMapper roomMasterMapper;


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Boolean doLockUserFund(String userId, Boolean bIsLocked, Integer sWalletType, String lockAction, String title) {

        try {
            Integer count = 3;
            while (true){
                // TRUE : 上鎖 ； FALSE : 解鎖
                // 钱包类别
                // 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款, 5: 单注验派
                int sNowIsLock = (bIsLocked) ? 0 : 1;
                int sToIsLock = (bIsLocked) ? 1 : 0;
                int iAffectNumber = (sWalletType == 0) ? 6 : 1;
                String sLockAction = lockAction + ((bIsLocked) ? " 上锁" : " 解锁");// TRUE : 上鎖 ; FALSE : 解鎖

                int updateCount = 0;

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
                count--;
                if (!(updateCount >= iAffectNumber) && bIsLocked) {
                    if(count == 0){
                        throw new RuntimeException("锁定/解锁用户资金失败 userId=" + userId);
                    }
                    Thread.sleep(10);
                }else {
                    break;
                }
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
    public Boolean addOrdersList(BetInfoEntity project, String title, int OrderType , RoomMasterEntity roomMaster) {
        try {
            UserFundEntity userFundSum = userFundMapper.selectByUserSum(title, project.getUserId()); //钱包全
            UserFundEntity o = new UserFundEntity();
            o.setUserid(project.getUserId());
            o.setWalletType(OrderType);
            if (OrderType == 8) {
                o.setWalletType(4);
            }
            UserFundEntity os = userFundMapper.selectByUserAndTypeOne(title, o); //频道钱包

            if (ObjectUtils.isEmpty(userFundSum) || ObjectUtils.isEmpty(os)) {
                throw new Exception("错误未查询到用户钱包");
            }
            //开始执行时间
            Date date = new Date();
            BigDecimal preChannelBalance = userFundSum.getChannelbalance();     //账变前 -帐变前频道资金
            BigDecimal preHoldBalance = userFundSum.getHoldbalance();           //账变前 -帐变前冻结资金
            BigDecimal preAvailableBalance = userFundSum.getAvailablebalance(); //账变前 -帐变前可用资金

            BigDecimal availableBalance = preAvailableBalance;                  //账变后 -帐变后可用资金
            BigDecimal holdBalance = preHoldBalance;                            //账变后 -帐变后的冻结资金
            BigDecimal channelBalance = preChannelBalance;                      //账变后 -帐变后频道资金

            BigDecimal amount = BigDecimal.valueOf(0);                          //注单带来的金额变化
            String titleAndDescription = "";
            switch (OrderType) {
                case 8:  //游戏扣款 --  结算时  wallet_type  4  channelbalance -amount  AND holdbalance -amount
                    amount= BigDecimal.valueOf(project.getTotalPrice());
                    holdBalance = preHoldBalance.subtract(amount);
                    titleAndDescription = "游戏扣款";
                    //修改注单为已结算
                    BetInfoEntity updateProject = new BetInfoEntity();
                    updateProject.setProjectId(project.getProjectId());
                    updateProject.setIsDeduct(1);                           //修改注单为已结算
                    updateProject.setDeductTime(date);                      //结算时间
                    updateProject.setUpdateTime(date);
                    updateProject.setUpdatedAt(date);
                    if (betInfoMapper.updateDeduct(updateProject, title)<=0){
                        throw new IllegalStateException("修改注单结算 资料失败");
                    }
                    //修改钱包
                    os.setChannelbalance(os.getChannelbalance().subtract(amount));//amount
                    os.setHoldbalance(os.getHoldbalance().subtract(amount));
                    os.setUpdatedAt(date);
                    if (userFundMapper.updateAddOrdersList(os, title)<=0){
                        throw new IllegalStateException("修改钱包 资料失败");
                    }
                    break;
                case 5: //奖金派送 -- 派奖时 wallet_type 5 + channelbalance  and availablebalance
                    amount = BigDecimal.valueOf(project.getBonus());
                    availableBalance = preAvailableBalance.add(amount);
                    channelBalance = availableBalance.add(amount);
                    titleAndDescription = "奖金派送";
                    if (betInfoMapper.updateIsGetprize1(project, title)<=0){
                        throw new IllegalStateException("修改注单中奖 资料失败");
                    }
                    //修改钱包
                    os.setChannelbalance(os.getChannelbalance().add(amount));//amount
                    os.setAvailablebalance(os.getAvailablebalance().add(amount));
                    os.setUpdatedAt(date);
                    if (userFundMapper.updateAddOrdersList(os, title)<=0){
                        throw new IllegalStateException("修改钱包 资料失败");
                    }
                    break;
            }

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
            order.setTitle(titleAndDescription);
            order.setAmount(amount);
            order.setDescription(titleAndDescription);
            order.setPreBalance(preChannelBalance);     //账变前 -帐变前频道资金
            order.setPreHold(preHoldBalance);           //账变前 -帐变前冻结资金
            order.setPreAvailable(preAvailableBalance); //账变前 -帐变前可用资金

            order.setChannelBalance(channelBalance);        //账变后 -帐变后可用资金
            order.setHoldBalance(holdBalance);              //账变后 -帐变后的冻结资金
            order.setAvailableBalance(availableBalance);    //账变后 -帐变后频道资金

            order.setUniqueKey(String.valueOf(System.currentTimeMillis()));
            order.setModes(project.getModes());
            order.setCreatedAt(date);
            order.setUpdatedAt(date);
            order.setActionTime(date);

            //生成抄单（Speculation）记录（依业务类型）
            roomMasterMapper.createSpeculation(roomMaster,order.getEntry());

            if (ordersMapper.addOrdersList(order, title) <= 0) {
                throw new IllegalStateException("新增Orders 资料失败");
            }

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
