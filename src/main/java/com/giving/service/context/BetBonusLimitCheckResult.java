package com.giving.service.context;

import com.giving.entity.BonusLimitUserIssueInfoEntity;
import com.giving.entity.VnBonusLimitEntity;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BetBonusLimitCheckResult {

    private List<BonusLimitUserIssueInfoEntity> userIssueLimits = new ArrayList<>();

    private List<VnBonusLimitEntity> vnBonusLimits = new ArrayList<>();
}
