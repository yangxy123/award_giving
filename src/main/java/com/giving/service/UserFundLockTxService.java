package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.RoomMasterEntity;

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
     * 添加账变记录-8/5
     * @param project
     * @param title
     * @return
     */
    Boolean addOrdersList(BetInfoEntity project, String title, int OrderType, RoomMasterEntity roomMaster);
}
