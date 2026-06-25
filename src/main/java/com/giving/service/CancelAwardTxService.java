package com.giving.service;

import com.giving.req.CancelAwardReq;
import com.giving.resp.CancelAwardResp;

import java.util.List;

/**
 * 撤销派奖事务服务
 */
public interface CancelAwardTxService {

    /**
     * 在事务中撤销派奖
     * @param title 厅主动态表前缀
     * @param req 撤销派奖请求
     * @param targetProjectIds 本次准备撤销的注单ID
     * @return 撤销结果
     */
    CancelAwardResp cancelAward(String title, CancelAwardReq req, List<String> targetProjectIds);
}
