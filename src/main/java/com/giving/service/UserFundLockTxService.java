package com.giving.service;

import com.giving.entity.BetInfoEntity;

public interface UserFundLockTxService {
    /**
     * 锁定结锁用户钱包
     * @param userId
     * @param bIsLocked
     * @param sWalletType
     * @param lockAction
     * @param title
     * @return
     */
    Boolean doLockUserFund(String userId, Boolean bIsLocked, Integer sWalletType, String lockAction,String title);

    /**
     * 添加账变记录-8
     * @param userId
     * @param project
     * @param title
     * @return
     */
    Boolean addOrdersList(String userId, BetInfoEntity project, String title);
}
