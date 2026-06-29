package com.giving.service;

import com.giving.req.BetOrderReq;
import com.giving.service.context.BetContext;

public interface BetAutooddsService {

    /**
     * 校验自动赔率停押、封锁和奖金刷新状态。
     */
    void validate(BetContext context, BetOrderReq req);
}
