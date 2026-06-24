package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.req.BetOrderReq;
import com.giving.service.context.BetBonusLimitCheckResult;
import com.giving.service.context.BetContext;

import java.util.List;

public interface BetBonusLimitService {

    /**
     * 校验投注奖金限额并生成事务内累计记录
     * @param context 投注上下文
     * @param req 投注请求
     * @param projects 已组装注单
     * @return 奖金限额累计记录
     */
    BetBonusLimitCheckResult checkAndBuild(BetContext context, BetOrderReq req, List<BetInfoEntity> projects);
}
