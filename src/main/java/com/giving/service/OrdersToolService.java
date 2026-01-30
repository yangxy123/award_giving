package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;

import java.util.List;

public interface OrdersToolService {

    /**
     * 批量订单处理
     * @param projects
     * @param title
     * @param OrderType
     * @return
     */
    Boolean getOrdersListAll(List<BetInfoEntity> projects, String title, int OrderType, RoomMasterEntity roomMaster);

    /**
     * 修改厅主录号
     * @param issueInfo
     */
    void updateRoomsIssueInfo(IssueInfoEntity issueInfo);

    /**
     * 修改厅主奖期结算状态
     * @param Issue
     * @param title
     * @return
     */
    Boolean updateIssueDeduct(TempIssueInfoEntity Issue, String title);
}
