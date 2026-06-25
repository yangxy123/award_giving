package com.giving.service.checker;

import com.giving.entity.MethodEntity;
import com.giving.req.LtProjectReq;
import com.giving.service.context.BetContext;

public interface LotteryBetChecker {

    /**
     * 按玩法规则计算服务端注数
     * @param context 投注上下文
     * @param project 投注项
     * @param method 玩法配置
     * @return 服务端注数，0表示校验失败
     */
    long calculate(BetContext context, LtProjectReq project, MethodEntity method);
}
