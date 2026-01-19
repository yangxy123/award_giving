package com.giving.service;

import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.req.DrawSourceReq;
import com.giving.req.ListIssueReq;

/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:47
 */
public interface AwardingProcessService {
    ApiResp<String> drawSource(DrawSourceReq req);

    ApiResp<String> resteDrawSource(ListIssueReq req);

    void lotteryDraw(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo);
}
