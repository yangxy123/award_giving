package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.req.BetOrderReq;



public interface BetService {
    /**
     * 投注
     * @param req
     * @return
     */
    ApiResp<String> order(BetOrderReq req);
}
