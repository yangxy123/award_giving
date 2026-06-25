package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.ProjectsTmpMapper;
import com.giving.mapper.UserFundMapper;
import com.giving.req.CancelAwardReq;
import com.giving.resp.CancelAwardResp;
import com.giving.service.CancelAwardTxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 撤销派奖事务服务实现
 */
@Service
public class CancelAwardTxServiceImpl implements CancelAwardTxService {
    private static final int WALLET_TYPE_CANCEL = 3;
    private static final int WALLET_TYPE_DEDUCT = 4;
    private static final int ORDER_TYPE_PRIZE = 5;
    private static final int ORDER_TYPE_DEDUCT = 8;
    private static final int ORDER_TYPE_CANCEL_AWARD = 12;

    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CancelAwardResp cancelAward(String title, CancelAwardReq req, List<String> targetProjectIds) {
        CancelAwardResp resp = buildBaseResp(title, req, targetProjectIds == null ? 0 : targetProjectIds.size());
        if (targetProjectIds != null && !targetProjectIds.isEmpty()) {
            List<BetInfoEntity> projects = betInfoMapper.selectAwardedProjectsByIdsForUpdate(
                    title, req.getLotteryId(), req.getIssue(), targetProjectIds);
            for (BetInfoEntity project : projects) {
                cancelProjectAward(title, req, project, resp);
            }
            resp.setSkippedProjectCount(resp.getTargetProjectCount() - resp.getCanceledProjectCount());
        }
        return resp;
    }

    private void cancelProjectAward(String title, CancelAwardReq req, BetInfoEntity project, CancelAwardResp resp) {
        Date now = new Date();
        OrdersEntity cancelOrder = null;
        if (needCancelPrize(project)) {
            cancelOrder = cancelProjectPrize(title, req, project, now);
            resp.setCanceledAmount(resp.getCanceledAmount().add(cancelOrder.getAmount()));
            resp.getCancelOrderIds().add(cancelOrder.getEntry());
        }

        OrdersEntity cancelDeductOrder = null;
        if (needCancelDeduct(project)) {
            cancelDeductOrder = cancelProjectDeduct(title, req, project, now);
            resp.setCanceledDeductAmount(resp.getCanceledDeductAmount().add(cancelDeductOrder.getAmount()));
            resp.getCancelDeductOrderIds().add(cancelDeductOrder.getEntry());
        }

        if (betInfoMapper.resetCancelAwardProject(title, project.getProjectId()) != 1) {
            throw new IllegalStateException("重置注单派奖状态失败，注单ID=" + project.getProjectId());
        }
        projectsTmpMapper.deleteByProjectId(title, project.getProjectId());

        resp.setCanceledProjectCount(resp.getCanceledProjectCount() + 1);
        resp.getProjectIds().add(project.getProjectId());
    }

    private OrdersEntity cancelProjectPrize(String title, CancelAwardReq req, BetInfoEntity project, Date now) {
        OrdersEntity sourcePrizeOrder = ordersMapper.selectUncancelledPrizeOrderForUpdate(
                title, project.getProjectId());
        if (sourcePrizeOrder == null || !Integer.valueOf(ORDER_TYPE_PRIZE).equals(sourcePrizeOrder.getOrderTypeId())) {
            throw new IllegalStateException("未找到可撤销的原派奖账变，注单ID=" + project.getProjectId());
        }

        UserFundEntity userFundSum = userFundMapper.selectByUserSum(title, project.getUserId());
        UserFundEntity cancelWallet = userFundMapper.selectByUserAndType(
                title, project.getUserId(), WALLET_TYPE_CANCEL);
        if (userFundSum == null || cancelWallet == null) {
            throw new IllegalStateException("未查询到用户撤单钱包，用户ID=" + project.getUserId());
        }
        if (!Integer.valueOf(1).equals(cancelWallet.getIslocked())) {
            throw new IllegalStateException("用户撤单钱包未锁定，用户ID=" + project.getUserId());
        }

        BigDecimal amount = oldBonus(project, sourcePrizeOrder);
        OrdersEntity cancelOrder = buildCancelOrder(req, project, sourcePrizeOrder, userFundSum, amount, now);
        applyCancelWallet(cancelWallet, amount, now);
        if (userFundMapper.updateCancelAwardFund(title, cancelWallet) != 1) {
            throw new IllegalStateException("更新撤单钱包失败，用户ID=" + project.getUserId());
        }
        if (ordersMapper.addCancelAwardOrder(cancelOrder, title) != 1) {
            throw new IllegalStateException("写入撤销派奖账变失败，注单ID=" + project.getProjectId());
        }
        return cancelOrder;
    }

    private OrdersEntity cancelProjectDeduct(String title, CancelAwardReq req, BetInfoEntity project, Date now) {
        OrdersEntity sourceDeductOrder = ordersMapper.selectUncancelledDeductOrderForUpdate(
                title, project.getProjectId());
        if (sourceDeductOrder == null || !Integer.valueOf(ORDER_TYPE_DEDUCT).equals(sourceDeductOrder.getOrderTypeId())) {
            throw new IllegalStateException("未找到可撤销的原结算账变，注单ID=" + project.getProjectId());
        }

        UserFundEntity deductWallet = userFundMapper.selectByUserAndType(
                title, project.getUserId(), WALLET_TYPE_DEDUCT);
        if (deductWallet == null) {
            throw new IllegalStateException("未查询到用户扣款钱包，用户ID=" + project.getUserId());
        }
        if (!Integer.valueOf(1).equals(deductWallet.getIslocked())) {
            throw new IllegalStateException("用户扣款钱包未锁定，用户ID=" + project.getUserId());
        }

        BigDecimal amount = totalPrice(project, sourceDeductOrder);
        applyCancelDeductWallet(deductWallet, amount, now);
        if (userFundMapper.updateLockedFund(title, deductWallet) != 1) {
            throw new IllegalStateException("更新扣款钱包失败，用户ID=" + project.getUserId());
        }
        if (ordersMapper.deleteOrderByEntryAndType(title, sourceDeductOrder.getEntry(), ORDER_TYPE_DEDUCT) != 1) {
            throw new IllegalStateException("删除原结算账变失败，注单ID=" + project.getProjectId());
        }
        return sourceDeductOrder;
    }

    private OrdersEntity buildCancelOrder(CancelAwardReq req, BetInfoEntity project, OrdersEntity sourcePrizeOrder,
                                          UserFundEntity userFundSum, BigDecimal amount, Date now) {
        BigDecimal preChannelBalance = nvl(userFundSum.getChannelbalance());
        BigDecimal preHoldBalance = nvl(userFundSum.getHoldbalance());
        BigDecimal preAvailableBalance = nvl(userFundSum.getAvailablebalance());

        OrdersEntity order = new OrdersEntity();
        String entry = OrdersToolServiceImpl.uniqId16();
        order.setEntry(entry);
        order.setLotteryId(project.getLotteryId());
        order.setMethodId(project.getMethodId());
        order.setTaskId(project.getTaskId());
        order.setProjectId(project.getProjectId());
        order.setFromuserId(project.getUserId());
        order.setOrderTypeId(ORDER_TYPE_CANCEL_AWARD);
        order.setIssue(project.getIssue());
        order.setTitle("撤销派奖");
        order.setAmount(amount);
        order.setDescription("撤销派奖");
        order.setPreBalance(preChannelBalance);
        order.setPreHold(preHoldBalance);
        order.setPreAvailable(preAvailableBalance);
        order.setChannelBalance(preChannelBalance.subtract(amount));
        order.setHoldBalance(preHoldBalance);
        order.setAvailableBalance(preAvailableBalance.subtract(amount));
        order.setTransferOrderId(sourcePrizeOrder.getEntry());
        order.setUniqueKey(project.getProjectId() + "_" + ORDER_TYPE_CANCEL_AWARD + "_" + sourcePrizeOrder.getEntry());
        order.setModes(project.getModes());
        order.setPlatform(StringUtils.hasText(req.getPlatform()) ? req.getPlatform() : project.getPlatform());
        order.setThirdPartyTrxId(project.getThirdPartyTrxId());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setActionTime(now);
        return order;
    }

    private void applyCancelWallet(UserFundEntity cancelWallet, BigDecimal amount, Date now) {
        cancelWallet.setChannelbalance(nvl(cancelWallet.getChannelbalance()).subtract(amount));
        cancelWallet.setAvailablebalance(nvl(cancelWallet.getAvailablebalance()).subtract(amount));
        cancelWallet.setHoldbalance(nvl(cancelWallet.getHoldbalance()));
        cancelWallet.setLastupdatetime(now);
        cancelWallet.setUpdatedAt(now);
    }

    private void applyCancelDeductWallet(UserFundEntity deductWallet, BigDecimal amount, Date now) {
        deductWallet.setChannelbalance(nvl(deductWallet.getChannelbalance()).add(amount));
        deductWallet.setAvailablebalance(nvl(deductWallet.getAvailablebalance()));
        deductWallet.setHoldbalance(nvl(deductWallet.getHoldbalance()).add(amount));
        deductWallet.setUpdatedAt(now);
    }

    private BigDecimal oldBonus(BetInfoEntity project, OrdersEntity sourcePrizeOrder) {
        if (project.getBonus() != null && project.getBonus() > 0) {
            return BigDecimal.valueOf(project.getBonus());
        }
        BigDecimal orderAmount = nvl(sourcePrizeOrder.getAmount());
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("原派奖金额异常，注单ID=" + project.getProjectId());
        }
        return orderAmount;
    }

    private BigDecimal totalPrice(BetInfoEntity project, OrdersEntity sourceDeductOrder) {
        if (project.getTotalPrice() != null && project.getTotalPrice() > 0) {
            return BigDecimal.valueOf(project.getTotalPrice());
        }
        BigDecimal orderAmount = nvl(sourceDeductOrder.getAmount());
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("原结算金额异常，注单ID=" + project.getProjectId());
        }
        return orderAmount;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean needCancelPrize(BetInfoEntity project) {
        return Integer.valueOf(1).equals(project.getIsGetprize())
                && Integer.valueOf(1).equals(project.getPrizeStatus());
    }

    private boolean needCancelDeduct(BetInfoEntity project) {
        return Integer.valueOf(1).equals(project.getIsDeduct());
    }

    private CancelAwardResp buildBaseResp(String title, CancelAwardReq req, int targetProjectCount) {
        CancelAwardResp resp = new CancelAwardResp();
        resp.setMasterId(req.getMasterId());
        resp.setTitle(title);
        resp.setLotteryId(req.getLotteryId());
        resp.setIssue(req.getIssue());
        resp.setCancelAll(Boolean.TRUE.equals(req.getCancelAll()));
        resp.setTargetProjectCount(targetProjectCount);
        resp.setSkippedProjectCount(targetProjectCount);
        return resp;
    }
}
