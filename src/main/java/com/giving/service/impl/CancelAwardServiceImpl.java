package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.pagehelper.PageHelper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.mapper.TempIssueInfoMapper;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.CancelAwardReq;
import com.giving.resp.CancelAwardResp;
import com.giving.service.CancelAwardService;
import com.giving.service.CancelAwardTxService;
import com.giving.service.UserFundLockTxService;
import com.giving.util.TableNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 撤销派奖服务实现
 */
@Service
public class CancelAwardServiceImpl implements CancelAwardService {
    private static final int WALLET_TYPE_CANCEL = 3;
    private static final int WALLET_TYPE_DEDUCT = 4;
    private static final int CANCEL_ALL_PAGE_SIZE = 300;
    private static final Logger log = LoggerFactory.getLogger(CancelAwardServiceImpl.class);
    private static final Set<String> RUNNING_CANCEL_ALL_TASKS = ConcurrentHashMap.newKeySet();

    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private UserFundLockTxService userFundLockTxService;
    @Autowired
    private CancelAwardTxService cancelAwardTxService;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;

    @Override
    public ApiResp<CancelAwardResp> cancelAward(CancelAwardReq req) {
        String title = null;
        try {
            validateScope(req);

            Integer masterId = parseMasterId(req.getMasterId());
            RoomMasterEntity roomMaster = roomMasterMapper.selectOne(new LambdaQueryWrapper<RoomMasterEntity>()
                    .eq(RoomMasterEntity::getMasterId, masterId));
            if (roomMaster == null || !Integer.valueOf(1).equals(roomMaster.getIsActive())) {
                return ApiResp.bussError("厅主不存在或未启用");
            }
            title = TableNameUtil.safePrefix(roomMaster.getTitle());

            if (Boolean.TRUE.equals(req.getCancelAll())) {
                startCancelAllThread(title, req);
                CancelAwardResp resp = buildStartedResp(title, req);
                return ApiResp.sucess(resp);
            }

            List<LockedWallet> lockedWallets = new ArrayList<>();
            String projectId = StringUtils.hasText(req.getProjectId()) ? req.getProjectId().trim() : null;
            List<BetInfoEntity> candidates = betInfoMapper.selectAwardedProjectsForCancel(
                    title, req.getLotteryId(), req.getIssue(), projectId);
            candidates.sort(Comparator
                    .comparing(BetInfoEntity::getUserId, Comparator.nullsLast(String::compareTo))
                    .thenComparing(BetInfoEntity::getProjectId, Comparator.nullsLast(String::compareTo)));

            List<String> targetProjectIds = candidates.stream()
                    .map(BetInfoEntity::getProjectId)
                    .collect(Collectors.toList());
            try {
                lockWallets(title, candidates, lockedWallets);
                CancelAwardResp resp = cancelAwardTxService.cancelAward(title, req, targetProjectIds);
                return ApiResp.sucess(resp);
            } finally {
                unlockWallets(title, lockedWallets);
            }
        } catch (IllegalArgumentException e) {
            return ApiResp.paramError(e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResp.bussError(e.getMessage());
        } catch (Exception e) {
            log.error("撤销派奖失败，厅主ID={}，彩种ID={}，奖期={}，注单ID={}，撤全部={}",
                    req.getMasterId(), req.getLotteryId(), req.getIssue(), req.getProjectId(), req.getCancelAll(), e);
            return ApiResp.bussError("撤销派奖失败");
        }
    }

    private void startCancelAllThread(String title, CancelAwardReq req) {
        CancelAwardReq asyncReq = copyReq(req);
        String taskKey = cancelAllTaskKey(title, asyncReq);
        if (!RUNNING_CANCEL_ALL_TASKS.add(taskKey)) {
            throw new IllegalStateException("整期撤销派奖任务正在执行，请勿重复提交");
        }
        new Thread(() -> cancelAllByPage(title, asyncReq),
                "cancel-award-" + title + "-" + req.getLotteryId() + "-" + req.getIssue()).start();
    }

    private void cancelAllByPage(String title, CancelAwardReq req) {
        int batchNo = 0;
        int totalCanceled = 0;
        String taskKey = cancelAllTaskKey(title, req);
        try {
            while (true) {
                PageHelper.startPage(1, CANCEL_ALL_PAGE_SIZE);
                List<BetInfoEntity> candidates = betInfoMapper.selectIssueProjectsForCancel(
                        title, req.getLotteryId(), req.getIssue());
                if (candidates == null || candidates.isEmpty()) {
                    break;
                }
                candidates.sort(Comparator
                        .comparing(BetInfoEntity::getUserId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(BetInfoEntity::getProjectId, Comparator.nullsLast(String::compareTo)));
                List<String> targetProjectIds = candidates.stream()
                        .map(BetInfoEntity::getProjectId)
                        .collect(Collectors.toList());
                List<LockedWallet> lockedWallets = new ArrayList<>();
                try {
                    lockWallets(title, candidates, lockedWallets);
                    CancelAwardResp batchResp = cancelAwardTxService.cancelAward(title, req, targetProjectIds);
                    totalCanceled += batchResp.getCanceledProjectCount();
                    batchNo++;
                    log.info("整期撤销派奖批次完成，厅主表名={}，彩种ID={}，奖期={}，批次={}，本批订单数={}，累计订单数={}",
                            title, req.getLotteryId(), req.getIssue(), batchNo,
                            batchResp.getCanceledProjectCount(), totalCanceled);
                } finally {
                    unlockWallets(title, lockedWallets);
                }
            }
            int updated = tempIssueInfoMapper.resetAwardStatus(title, req.getLotteryId(), req.getIssue());
            log.info("整期撤销派奖完成，厅主表名={}，彩种ID={}，奖期={}，批次={}，订单数={}，奖期状态重置={}",
                    title, req.getLotteryId(), req.getIssue(), batchNo, totalCanceled, updated > 0);
        } catch (Exception e) {
            log.error("整期撤销派奖异步任务失败，厅主表名={}，彩种ID={}，奖期={}，已完成批次={}",
                    title, req.getLotteryId(), req.getIssue(), batchNo, e);
        } finally {
            RUNNING_CANCEL_ALL_TASKS.remove(taskKey);
        }
    }

    private String cancelAllTaskKey(String title, CancelAwardReq req) {
        return title + ":" + req.getLotteryId() + ":" + req.getIssue();
    }

    private void lockWallets(String title, List<BetInfoEntity> candidates, List<LockedWallet> lockedWallets) {
        List<String> cancelWalletUserIds = candidates.stream()
                .filter(this::needCancelAwardWallet)
                .map(BetInfoEntity::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        for (String userId : cancelWalletUserIds) {
            if (!userFundLockTxService.doLockUserFund(
                    userId, true, WALLET_TYPE_CANCEL, "CancelAward_001", title)) {
                throw new IllegalStateException("锁定撤单钱包失败，用户ID=" + userId);
            }
            lockedWallets.add(new LockedWallet(userId, WALLET_TYPE_CANCEL));
        }

        List<String> deductWalletUserIds = candidates.stream()
                .filter(this::needCancelDeductWallet)
                .map(BetInfoEntity::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        for (String userId : deductWalletUserIds) {
            if (!userFundLockTxService.doLockUserFund(
                    userId, true, WALLET_TYPE_DEDUCT, "CancelAwardDeduct_001", title)) {
                throw new IllegalStateException("锁定扣款钱包失败，用户ID=" + userId);
            }
            lockedWallets.add(new LockedWallet(userId, WALLET_TYPE_DEDUCT));
        }
    }

    private void unlockWallets(String title, List<LockedWallet> lockedWallets) {
        Collections.reverse(lockedWallets);
        for (LockedWallet lockedWallet : lockedWallets) {
            if (!userFundLockTxService.doLockUserFund(
                    lockedWallet.userId, false, lockedWallet.walletType, "CancelAward_002", title)) {
                log.error("撤销派奖结束后钱包解锁失败，厅主表名={}，用户ID={}，钱包类型={}",
                        title, lockedWallet.userId, lockedWallet.walletType);
            }
        }
    }

    private boolean needCancelAwardWallet(BetInfoEntity project) {
        return Integer.valueOf(1).equals(project.getIsGetprize())
                && Integer.valueOf(1).equals(project.getPrizeStatus());
    }

    private boolean needCancelDeductWallet(BetInfoEntity project) {
        return Integer.valueOf(1).equals(project.getIsDeduct());
    }

    private CancelAwardReq copyReq(CancelAwardReq req) {
        CancelAwardReq copy = new CancelAwardReq();
        copy.setMasterId(req.getMasterId());
        copy.setLotteryId(req.getLotteryId());
        copy.setIssue(req.getIssue());
        copy.setProjectId(req.getProjectId());
        copy.setCancelAll(req.getCancelAll());
        copy.setPlatform(req.getPlatform());
        return copy;
    }

    private CancelAwardResp buildStartedResp(String title, CancelAwardReq req) {
        CancelAwardResp resp = new CancelAwardResp();
        resp.setMasterId(req.getMasterId());
        resp.setTitle(title);
        resp.setLotteryId(req.getLotteryId());
        resp.setIssue(req.getIssue());
        resp.setCancelAll(true);
        resp.setAsyncStarted(true);
        return resp;
    }

    private void validateScope(CancelAwardReq req) {
        boolean cancelAll = Boolean.TRUE.equals(req.getCancelAll());
        boolean hasProjectId = StringUtils.hasText(req.getProjectId());
        if (cancelAll && hasProjectId) {
            throw new IllegalArgumentException("撤全部时不能同时传注单ID");
        }
        if (!cancelAll && !hasProjectId) {
            throw new IllegalArgumentException("单笔撤销请传projectId；整期撤销请传cancelAll=true");
        }
        if (StringUtils.hasText(req.getProjectId())) {
            req.setProjectId(req.getProjectId().trim());
        }
        if (StringUtils.hasText(req.getIssue())) {
            req.setIssue(req.getIssue().trim());
        }
    }

    private Integer parseMasterId(String masterId) {
        try {
            return Integer.valueOf(masterId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("厅组id格式错误");
        }
    }

    private static class LockedWallet {
        private final String userId;
        private final Integer walletType;

        private LockedWallet(String userId, Integer walletType) {
            this.userId = userId;
            this.walletType = walletType;
        }
    }
}
