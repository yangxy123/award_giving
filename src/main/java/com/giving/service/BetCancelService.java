package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.req.BetCancelProjectReq;
import com.giving.resp.BetCancelProjectResp;

/**
 * 撤单服务
 */
public interface BetCancelService {

    /**
     * 用户撤销普通注单
     * @param req 撤单请求
     * @return 撤单结果
     */
    ApiResp<BetCancelProjectResp> cancelProject(BetCancelProjectReq req);
}
