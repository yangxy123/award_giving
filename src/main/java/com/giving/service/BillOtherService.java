package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.req.BillOpenReq;
import com.giving.req.BillSetApiReq;

/**
 * @author zzby
 * @version 创建时间： 2026/1/30 下午3:56
 */
public interface BillOtherService {
    /**
     * 设置盈利率
     * @param threshold
     * @return
     */
    ApiResp<String> nowThreshold(String threshold);

    /**
     * 设置开票机url
     * @param req
     * @return
     */
    ApiResp<String> setBillUrl(BillSetApiReq req);

    /**
     * 获取开票机url
     * @return
     */
    ApiResp<String> getBillUrl();

    /**
     * open
     * @param req
     * @return
     */
    ApiResp<String> open(BillOpenReq req);
}
