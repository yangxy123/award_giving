package com.giving.service;

import com.giving.req.BetOrderReq;
import com.giving.service.context.BetContext;

public interface BetContentValidationService {

    /**
     * 校验投注内容、服务端注数和金额
     * @param req 投注请求
     * @param context 投注上下文
     */
    void validate(BetOrderReq req, BetContext context);
}
