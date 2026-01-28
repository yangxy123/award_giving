package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.req.ListIssueReq;
import com.giving.req.ManualDistributionReq;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:11
 */
public interface OPissueToolService {
    /**
     * 已经录号完全 -- 执行尚未派奖订单
     * @param req
     * @return
     */
    ApiResp<String> resteDrawSource(ListIssueReq req);

    ApiResp<String> manualDistribution(ManualDistributionReq req);

    /**
     * 订单结算
     * @param req
     * @return
     */
    ApiResp<String> doCongealToReal(ManualDistributionReq req);

    /**
     * 强制手动结算
     * @param req
     * @return
     */
    ApiResp<String> doForceCongealToReal(ManualDistributionReq req);
}
