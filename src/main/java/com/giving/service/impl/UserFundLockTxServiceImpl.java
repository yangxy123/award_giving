package com.giving.service.impl;

import com.giving.entity.UserFundEntity;
import com.giving.mapper.UserFundMapper;
import com.giving.service.UserFundLockTxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.Resource;

@Service
public class UserFundLockTxServiceImpl implements UserFundLockTxService {
    @Resource
    private UserFundMapper userFundMapper;

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
}
