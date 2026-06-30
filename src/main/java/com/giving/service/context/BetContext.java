package com.giving.service.context;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.MethodEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.entity.UserEntity;
import com.giving.req.LtProjectReq;
import lombok.Data;
import org.springframework.util.StringUtils;

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

    private TempIssueInfoEntity issue;

    private Map<Integer, LotteryEntity> lotteryMap = new HashMap<>();

    private Map<String, TempIssueInfoEntity> issueMap = new HashMap<>();

    private Map<Integer, MethodEntity> methodMap = new HashMap<>();

    private BigDecimal currencyRate = BigDecimal.ONE;

    private List<BetInfoEntity> projectList = new ArrayList<>();

    private String title;

    private String errorMessage;

    public LotteryEntity getLotteryById(Integer lotteryId) {
        if (lotteryId == null) {
            return lottery;
        }
        LotteryEntity value = lotteryMap.get(lotteryId);
        return value == null ? lottery : value;
    }

    public TempIssueInfoEntity getIssueByProject(LtProjectReq project) {
        if (project == null) {
            return issue;
        }
        TempIssueInfoEntity value = getIssueByLotteryAndIssue(project.getLotteryId(), project.getIssue());
        return value == null ? issue : value;
    }

    public TempIssueInfoEntity getIssueByLotteryAndIssue(Integer lotteryId, String issueNo) {
        if (lotteryId == null || !StringUtils.hasText(issueNo)) {
            return null;
        }
        return issueMap.get(issueKey(lotteryId, issueNo));
    }

    public void putIssue(TempIssueInfoEntity issueInfo) {
        if (issueInfo == null || issueInfo.getLotteryId() == null || !StringUtils.hasText(issueInfo.getIssue())) {
            return;
        }
        issueMap.put(issueKey(issueInfo.getLotteryId().intValue(), issueInfo.getIssue()), issueInfo);
    }

    private String issueKey(Integer lotteryId, String issueNo) {
        return lotteryId + "|" + issueNo;
    }
}
