package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.req.CancelAwardReq;
import com.giving.resp.CancelAwardResp;

/**
 * 撤销派奖服务
 */
public interface CancelAwardService {

    /**
     * 撤销指定注单或整期全部已派奖注单
     * @param req 撤销派奖请求
     * @return 撤销结果
     */
    ApiResp<CancelAwardResp> cancelAward(CancelAwardReq req);
}
