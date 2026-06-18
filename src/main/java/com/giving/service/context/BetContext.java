package com.giving.service.context;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.MethodEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.UserEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class BetContext {

    private RoomMasterEntity roomMaster;

    private UserEntity user;

    private LotteryEntity lottery;

    private IssueInfoEntity issue;

    private Map<Integer, MethodEntity> methodMap = new HashMap<>();

    private BigDecimal currencyRate = BigDecimal.ONE;

    private List<BetInfoEntity> projectList = new ArrayList<>();

    private String title;

    private String errorMessage;
}
