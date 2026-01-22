package com.giving.service;

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
}
