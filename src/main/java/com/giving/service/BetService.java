package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.req.BetOrderReq;
import com.giving.resp.BetOrderResp;

public interface BetService {
    /**
     * 投注
     * @param req 投注请求
     * @return 投注结果
     */
    ApiResp<BetOrderResp> order(BetOrderReq req);
}
