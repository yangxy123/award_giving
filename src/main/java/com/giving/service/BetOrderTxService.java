package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.ProjectsTmpEntity;

import java.math.BigDecimal;
import java.util.List;

public interface BetOrderTxService {

    void createOrder(String title, String userId, int walletType, BigDecimal totalAmount,
                     List<BetInfoEntity> projects, List<ProjectsTmpEntity> projectsTmp,
                     List<OrdersEntity> orders);
}
