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
    /**
     * 录号派奖
     * @param req
     * @return
     */
    ApiResp<String> drawSource(DrawSourceReq req);

    void updateRoomsIssueInfo(IssueInfoEntity issueInfo);

    void lotteryDraw(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo);
}
