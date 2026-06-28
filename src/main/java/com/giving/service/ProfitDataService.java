package com.giving.service;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.RoomMasterEntity;

import java.util.List;

public interface ProfitDataService {
    void addPriceAfterCommit(RoomMasterEntity roomMaster, List<BetInfoEntity> projects);

    void addBonusAfterCommit(RoomMasterEntity roomMaster, List<BetInfoEntity> projects);
}
