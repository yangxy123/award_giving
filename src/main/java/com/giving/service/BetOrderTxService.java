package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.BonusLimitUserIssueInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.ProjectsTmpEntity;
import com.giving.entity.TempUserDiffpointsEntity;
import com.giving.entity.VnBonusLimitEntity;

import java.math.BigDecimal;
import java.util.List;

public interface BetOrderTxService {

    /**
     * 创建投注订单并写入相关累计数据
     * @param title 厅主动态表前缀
     * @param userId 用户ID
     * @param walletType 钱包类型
     * @param totalAmount 投注总额
     * @param projects 注单列表
     * @param projectsTmp 临时注单列表
     * @param orders 账变列表
     * @param userDiffpoints 返点记录
     * @param userIssueLimits 用户单期奖金累计
     * @param vnBonusLimits 泰国/越南号码累计
     */
    void createOrder(String title, String userId, int walletType, BigDecimal totalAmount,
                     List<BetInfoEntity> projects, List<ProjectsTmpEntity> projectsTmp,
                     List<OrdersEntity> orders, List<TempUserDiffpointsEntity> userDiffpoints,
                     List<BonusLimitUserIssueInfoEntity> userIssueLimits,
                     List<VnBonusLimitEntity> vnBonusLimits);
}
