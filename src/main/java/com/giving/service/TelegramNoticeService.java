package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.RoomMasterEntity;

import java.util.List;

public interface TelegramNoticeService {

    /**
     * 大额奖金派奖预警，事务提交后异步发送。
     *
     * @param roomMaster 厅主信息
     * @param projects 已成功派奖的注单
     */
    void sendLargeBonusAwardWarningAfterCommit(RoomMasterEntity roomMaster, List<BetInfoEntity> projects);
}
