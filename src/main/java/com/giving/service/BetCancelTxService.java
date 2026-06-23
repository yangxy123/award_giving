package com.giving.service;

import com.giving.entity.RoomMasterEntity;
import com.giving.req.BetCancelProjectReq;
import com.giving.resp.BetCancelProjectResp;

/**
 * 撤单事务服务
 */
public interface BetCancelTxService {

    /**
     * 在独立事务中撤销普通注单
     * @param title 厅主动态表前缀
     * @param roomMaster 厅主
     * @param req 撤单请求
     * @param cancelType 撤单类型，1=用户撤单，2=公司撤单
     * @return 撤单结果
     */
    BetCancelProjectResp cancelProject(String title, RoomMasterEntity roomMaster,
                                       BetCancelProjectReq req, int cancelType);
}
