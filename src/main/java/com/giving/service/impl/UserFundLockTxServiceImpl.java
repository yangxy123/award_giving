package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.UserFundMapper;
import com.giving.service.UserFundLockTxService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

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

            LambdaQueryWrapper<UserFundEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserFundEntity::getUserid, userId);
            wrapper.eq(UserFundEntity::getIslocked, sNowIsLock);
            wrapper.eq(UserFundEntity::getWalletType, sWalletType);
            List<UserFundEntity> userFundS;
            if (sWalletType > 0) {
                userFundS = userFundMapper.selectByUserAndTypeLock(title,userId,sNowIsLock,sWalletType);
            }else {
                userFundS = userFundMapper.selectByUserAndTypeAll(title,userId,sNowIsLock);
            }


            int updateCount = 0;
            //实际执行-锁定|解锁
            for (UserFundEntity userFund : userFundS) {
                userFund.setIslocked(sToIsLock);
                userFund.setLockAction(sLockAction);
                updateCount += userFundMapper.updateLockedById(title,userFund); // 每次返回 0/1
            }
            if (!(updateCount >= iAffectNumber) && bIsLocked) {
                throw new RuntimeException("锁定/解锁用户资金失败 userId=" + userId);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
