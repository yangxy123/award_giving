package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.ProjectsTmpEntity;
import com.giving.entity.TempUserDiffpointsEntity;

import java.math.BigDecimal;
import java.util.List;

public interface BetOrderTxService {

    void createOrder(String title, String userId, int walletType, BigDecimal totalAmount,
                     List<BetInfoEntity> projects, List<ProjectsTmpEntity> projectsTmp,
                     List<OrdersEntity> orders, List<TempUserDiffpointsEntity> userDiffpoints);
}
