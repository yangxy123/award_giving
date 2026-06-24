package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.entity.UserDiffpointsEntity;
import com.giving.entity.UserFundEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.ProjectsTmpMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.mapper.TempIssueInfoMapper;
import com.giving.mapper.UserDiffpointsMapper;
import com.giving.mapper.UserFundMapper;
import com.giving.req.BetCancelProjectReq;
import com.giving.resp.BetCancelProjectResp;
import com.giving.service.BetCancelTxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 撤单事务服务实现
 */
@Service
public class BetCancelTxServiceImpl implements BetCancelTxService {
    private static final int WALLET_TYPE_CANCEL = 3;
    private static final int ORDER_TYPE_CANCEL_USER = 9;
    private static final int ORDER_TYPE_CANCEL_AFTER_DEDUCT = 99;
    private static final int ORDER_TYPE_CANCEL_POINT = 11;
    private static final int CANCEL_STATUS_USER = 1;
    private static final int LOTTERY_ID_MINI_GAME = 55;

    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private UserDiffpointsMapper userDiffpointsMapper;
    @Autowired
    private RoomMasterMapper roomMasterMapper;

    /**
     * 在独立事务中撤销普通注单
     * @param title 厅主动态表前缀
     * @param roomMaster 厅主
     * @param req 撤单请求
     * @param cancelType 撤单类型，1=用户撤单，2=公司撤单
     * @return 撤单结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BetCancelProjectResp cancelProject(String title, RoomMasterEntity roomMaster,
                                              BetCancelProjectReq req, int cancelType) {
        BetInfoEntity project = betInfoMapper.selectProjectByIdForUpdate(title, req.getProjectId());
        validateProject(project, req);
        validateIssueCancelTime(title, project);

        UserFundEntity userFundSum = userFundMapper.selectByUserSum(title, req.getUserId());
        UserFundEntity cancelWallet = userFundMapper.selectByUserAndType(
                title, req.getUserId(), WALLET_TYPE_CANCEL);
        if (userFundSum == null || cancelWallet == null) {
            throw new IllegalStateException("未查询到用户撤单钱包");
        }

        BigDecimal amount = toMoney(project.getTotalPrice());
        Date now = new Date();
        List<OrdersEntity> orders = new ArrayList<>();
        BalanceSnapshot balance = new BalanceSnapshot(userFundSum, cancelWallet);

        int cancelOrderType = Integer.valueOf(1).equals(project.getIsDeduct())
                ? ORDER_TYPE_CANCEL_AFTER_DEDUCT
                : ORDER_TYPE_CANCEL_USER;
        OrdersEntity cancelOrder = applyCancelRefund(req, project, amount, now, balance, cancelOrderType);
        orders.add(cancelOrder);

        List<UserDiffpointsEntity> diffpoints = userDiffpointsMapper.selectByProjectId(title, project.getProjectId());
        if (diffpoints != null && !diffpoints.isEmpty()) {
            applyPointCancel(title, req, project, now, balance, diffpoints, orders);
        }

        if (userFundMapper.updateLockedFund(title, cancelWallet) != 1) {
            throw new IllegalStateException("更新撤单钱包失败");
        }
        if (betInfoMapper.updateCancelStatus(
                title, project.getProjectId(), project.getUserId(), project.getIsDeduct(), cancelType) != 1) {
            throw new IllegalStateException("更新注单撤单状态失败");
        }
        if (ordersMapper.addOrdersListAll(orders, title) != orders.size()) {
            throw new IllegalStateException("写入撤单账变失败");
        }
        projectsTmpMapper.deleteByProjectId(title, project.getProjectId());
        createSpeculationIfNeeded(roomMaster, orders);

        BetCancelProjectResp resp = new BetCancelProjectResp();
        resp.setProjectId(project.getProjectId());
        resp.setCancelOrderId(cancelOrder.getEntry());
        resp.setCancelOrderTypeId(cancelOrderType);
        resp.setAmount(amount);
        resp.setAvailableBalance(balance.availableBalance);
        return resp;
    }

    /**
     * 校验注单是否可撤销
     * @param project 注单
     * @param req 撤单请求
     */
    private void validateProject(BetInfoEntity project, BetCancelProjectReq req) {
        if (project == null || !req.getUserId().equals(project.getUserId())) {
            throw new IllegalStateException("只能对自己的投注进行撤单");
        }
        if (Integer.valueOf(LOTTERY_ID_MINI_GAME).equals(project.getLotteryId())) {
            throw new IllegalStateException("该彩种不能撤单");
        }
        if (project.getIsCancel() != null && project.getIsCancel() != 0) {
            throw new IllegalStateException("注单已撤单");
        }
        if (project.getPrizeStatus() != null && project.getPrizeStatus() != 0) {
            throw new IllegalStateException("注单已派奖，需先撤销派奖");
        }
    }

    /**
     * 校验用户撤单时间
     * @param title 厅主动态表前缀
     * @param project 注单
     */
    private void validateIssueCancelTime(String title, BetInfoEntity project) {
        TempIssueInfoEntity issue = tempIssueInfoMapper.selectByTitle(
                title, Long.valueOf(project.getLotteryId()), project.getIssue());
        if (issue == null) {
            throw new IllegalStateException("找不到对应的资料");
        }
        Date saleEnd = issue.getSaleEnd();
        if (saleEnd == null || !new Date().before(saleEnd)) {
            throw new IllegalStateException("注单已超过可撤单时间");
        }
    }

    /**
     * 应用撤单返款资金变化
     * @param req 撤单请求
     * @param project 注单
     * @param amount 金额
     * @param now 当前时间
     * @param balance 余额快照
     * @param orderType 账变类型
     * @return 撤单返款账变
     */
    private OrdersEntity applyCancelRefund(BetCancelProjectReq req, BetInfoEntity project, BigDecimal amount,
                                           Date now, BalanceSnapshot balance, int orderType) {
        if (ORDER_TYPE_CANCEL_AFTER_DEDUCT == orderType) {
            balance.channelBalance = balance.channelBalance.add(amount);
            balance.availableBalance = balance.availableBalance.add(amount);
            balance.cancelWallet.setChannelbalance(nvl(balance.cancelWallet.getChannelbalance()).add(amount));
            balance.cancelWallet.setAvailablebalance(nvl(balance.cancelWallet.getAvailablebalance()).add(amount));
        } else {
            balance.holdBalance = balance.holdBalance.subtract(amount);
            balance.availableBalance = balance.availableBalance.add(amount);
            balance.cancelWallet.setHoldbalance(nvl(balance.cancelWallet.getHoldbalance()).subtract(amount));
            balance.cancelWallet.setAvailablebalance(nvl(balance.cancelWallet.getAvailablebalance()).add(amount));
        }
        balance.cancelWallet.setUpdatedAt(now);
        String description = ORDER_TYPE_CANCEL_AFTER_DEDUCT == orderType
                ? "撤单返款[已完成真实扣款]"
                : "玩家撤单返款";
        return buildOrder(req, project, orderType, amount, description, now, balance);
    }

    /**
     * 应用撤销返点资金变化
     * @param req 撤单请求
     * @param project 注单
     * @param now 当前时间
     * @param balance 余额快照
     * @param diffpoints 返点记录
     * @param orders 账变集合
     */
    private void applyPointCancel(String title, BetCancelProjectReq req, BetInfoEntity project, Date now,
                                  BalanceSnapshot balance, List<UserDiffpointsEntity> diffpoints,
                                  List<OrdersEntity> orders) {
        int paidStatusCount = 0;
        int unpaidStatusCount = 0;
        for (UserDiffpointsEntity diffpoint : diffpoints) {
            Integer status = diffpoint.getStatus();
            if (Integer.valueOf(1).equals(status)) {
                paidStatusCount++;
                if (nvl(diffpoint.getDiffmoney()).compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal amount = nvl(diffpoint.getDiffmoney());
                    balance.channelBalance = balance.channelBalance.subtract(amount);
                    balance.availableBalance = balance.availableBalance.subtract(amount);
                    balance.cancelWallet.setChannelbalance(nvl(balance.cancelWallet.getChannelbalance()).subtract(amount));
                    balance.cancelWallet.setAvailablebalance(nvl(balance.cancelWallet.getAvailablebalance()).subtract(amount));
                    orders.add(buildOrder(req, project, ORDER_TYPE_CANCEL_POINT, amount, "撤销返点", now, balance));
                }
            } else if (Integer.valueOf(0).equals(status)) {
                unpaidStatusCount++;
            }
        }
        if (paidStatusCount > 0) {
            if (userDiffpointsMapper.cancelPaidByProjectId(
                    title, project.getProjectId(), CANCEL_STATUS_USER) < paidStatusCount) {
                throw new IllegalStateException("更新已派返点撤销状态失败");
            }
            betInfoMapper.resetPointStatus(title, project.getProjectId());
        }
        if (unpaidStatusCount > 0) {
            if (userDiffpointsMapper.cancelUnpaidByProjectId(
                    title, project.getProjectId(), CANCEL_STATUS_USER) < unpaidStatusCount) {
                throw new IllegalStateException("更新未派返点撤单状态失败");
            }
        }
    }

    /**
     * 构建账变
     * @param req 撤单请求
     * @param project 注单
     * @param orderType 账变类型
     * @param amount 金额
     * @param description 描述
     * @param now 当前时间
     * @param balance 余额快照
     * @return 账变
     */
    private OrdersEntity buildOrder(BetCancelProjectReq req, BetInfoEntity project, int orderType, BigDecimal amount,
                                    String description, Date now, BalanceSnapshot balance) {
        OrdersEntity order = new OrdersEntity();
        String entry = OrdersToolServiceImpl.uniqId16();
        order.setEntry(entry);
        order.setLotteryId(project.getLotteryId());
        order.setMethodId(project.getMethodId());
        order.setTaskId(project.getTaskId());
        order.setProjectId(project.getProjectId());
        order.setFromuserId(project.getUserId());
        order.setOrderTypeId(orderType);
        order.setIssue(project.getIssue());
        order.setTitle(description);
        order.setAmount(amount);
        order.setDescription(description);
        order.setPreBalance(balance.preChannelBalance);
        order.setPreHold(balance.preHoldBalance);
        order.setPreAvailable(balance.preAvailableBalance);
        order.setChannelBalance(balance.channelBalance);
        order.setHoldBalance(balance.holdBalance);
        order.setAvailableBalance(balance.availableBalance);
        order.setUniqueKey(project.getProjectId() + "_" + orderType + "_" + entry);
        order.setModes(project.getModes());
        order.setPlatform(StringUtils.hasText(req.getPlatform()) ? req.getPlatform() : project.getPlatform());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setActionTime(now);
        balance.refreshPre();
        return order;
    }

    /**
     * 写入抄单表
     * @param roomMaster 厅主
     * @param orders 账变集合
     */
    private void createSpeculationIfNeeded(RoomMasterEntity roomMaster, List<OrdersEntity> orders) {
        if (roomMaster == null || roomMaster.getUserWalletType() == null || orders.isEmpty()) {
            return;
        }
        if (Arrays.asList(0, 1, 2, 3).contains(roomMaster.getUserWalletType())) {
            roomMasterMapper.createSpeculationList(roomMaster, orders);
        }
    }

    /**
     * 金额转BigDecimal
     * @param amount Double金额
     * @return BigDecimal金额
     */
    private BigDecimal toMoney(Double amount) {
        if (amount == null) {
            throw new IllegalStateException("注单金额异常");
        }
        return BigDecimal.valueOf(amount);
    }

    /**
     * 空金额转零
     * @param value 金额
     * @return 非空金额
     */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 余额快照
     */
    private class BalanceSnapshot {
        private final UserFundEntity cancelWallet;
        private BigDecimal preChannelBalance;
        private BigDecimal preHoldBalance;
        private BigDecimal preAvailableBalance;
        private BigDecimal channelBalance;
        private BigDecimal holdBalance;
        private BigDecimal availableBalance;

        private BalanceSnapshot(UserFundEntity userFundSum, UserFundEntity cancelWallet) {
            this.cancelWallet = cancelWallet;
            this.cancelWallet.setChannelbalance(nvl(cancelWallet.getChannelbalance()));
            this.cancelWallet.setHoldbalance(nvl(cancelWallet.getHoldbalance()));
            this.cancelWallet.setAvailablebalance(nvl(cancelWallet.getAvailablebalance()));
            this.preChannelBalance = nvl(userFundSum.getChannelbalance());
            this.preHoldBalance = nvl(userFundSum.getHoldbalance());
            this.preAvailableBalance = nvl(userFundSum.getAvailablebalance());
            this.channelBalance = preChannelBalance;
            this.holdBalance = preHoldBalance;
            this.availableBalance = preAvailableBalance;
        }

        /**
         * 刷新下一笔账变的前置余额
         */
        private void refreshPre() {
            this.preChannelBalance = channelBalance;
            this.preHoldBalance = holdBalance;
            this.preAvailableBalance = availableBalance;
        }
    }
}
