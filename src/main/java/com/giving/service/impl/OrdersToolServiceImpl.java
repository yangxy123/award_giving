package com.giving.service.impl;

import com.giving.entity.*;
import com.giving.mapper.*;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import com.giving.service.OrdersToolService;
import com.giving.service.UserFundLockTxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 修改注单状态，修改厅主奖期信息，
 */
@Service
public class OrdersToolServiceImpl implements OrdersToolService {

    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private UserFundLockTxService userFundLockTxService;
    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private AwardingProcessService awardingProcessService;


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Boolean getOrdersListAll(List<BetInfoEntity> projects, String title, int OrderType, RoomMasterEntity roomMaster) {
        try {
            List<OrdersEntity> ordersList = new ArrayList<>();  //需要新增的orders
            List<BetInfoEntity> betInfos = new ArrayList<>();   //需要修改的project
            Map<String, UserFundEntity> userFundMap = new HashMap<>();
            Map<String, UserFundEntity> userFundSunMap = new HashMap<>(); //需要更新的钱包

            List<BetInfoEntity> errorBetInfoList = new ArrayList<>();
            for (BetInfoEntity project : projects) {
                UserFundEntity userFundSum;
                UserFundEntity os;
                if (userFundMap.containsKey(project.getUserId()) && userFundSunMap.containsKey(project.getUserId())) {
                    //存在
                    userFundSum = userFundSunMap.get(project.getUserId());
                    os = userFundMap.get(project.getUserId());
                }
                else {
                    userFundSum = userFundMapper.selectByUserSum(title, project.getUserId()); //钱包全
                    UserFundEntity o = new UserFundEntity();
                    o.setUserid(project.getUserId());
                    o.setWalletType(OrderType);
                    if (OrderType == 8) {
                        o.setWalletType(4);
                    }
                    os = userFundMapper.selectByUserAndTypeOne(title, o); //频道钱包
                    userFundMap.putIfAbsent(project.getUserId(),os);
                    userFundSunMap.putIfAbsent(project.getUserId(), userFundSum);
                    String lockAction = OrderType==5?"CP_001":"CR_001";
                    if (!userFundLockTxService.doLockUserFund(project.getUserId(), true, o.getWalletType(), lockAction, title)) {
//                        throw new RuntimeException("--锁定用户钱包失败");
                        errorBetInfoList.add(project);
                        continue;
                    }
                }
                //开始执行时间
                Date date = new Date();
                BigDecimal preChannelBalance = userFundSum.getChannelbalance();     //账变前 -帐变前频道资金
                BigDecimal preHoldBalance = userFundSum.getHoldbalance();           //账变前 -帐变前冻结资金
                BigDecimal preAvailableBalance = userFundSum.getAvailablebalance(); //账变前 -帐变前可用资金

                BigDecimal availableBalance = preAvailableBalance;                  //账变后 -帐变后可用资金
                BigDecimal holdBalance = preHoldBalance;                            //账变后 -帐变后的冻结资金
                BigDecimal channelBalance = preChannelBalance;                      //账变后 -帐变后频道资金

                BigDecimal amount = BigDecimal.valueOf(0);                          //注单带来的金额变化
                String titleAndDescription = "";
                switch (OrderType) {
                    case 8:  //游戏扣款 --  结算时  wallet_type  4  channelbalance -amount  AND holdbalance -amount
                        amount= BigDecimal.valueOf(project.getTotalPrice());
                        holdBalance = preHoldBalance.subtract(amount);
                        titleAndDescription = "游戏扣款";
                        //修改注单为已结算
                        project.setProjectId(project.getProjectId());
                        project.setIsDeduct(1);                           //修改注单为已结算
                        project.setDeductTime(date);                      //结算时间
                        project.setUpdateTime(date);
                        project.setUpdatedAt(date);
                        //修改计算-SUM
                        userFundSum.setChannelbalance(userFundSum.getChannelbalance().subtract(amount));
                        userFundSum.setHoldbalance(userFundSum.getHoldbalance().subtract(amount));
                        //修改钱包
                        os.setChannelbalance(os.getChannelbalance().subtract(amount));//amount
                        os.setHoldbalance(os.getHoldbalance().subtract(amount));
                        os.setUpdatedAt(date);
                        break;
                    case 5: //奖金派送 -- 派奖时 wallet_type 5 + channelbalance  and availablebalance
                        amount = BigDecimal.valueOf(project.getBonus());
                        availableBalance = preAvailableBalance.add(amount);
                        channelBalance = availableBalance.add(amount);
                        titleAndDescription = "奖金派送";
                        //已经派奖
                        project.setPrizeStatus(1);
                        //修改计算-SUM
                        userFundSum.setChannelbalance(userFundSum.getChannelbalance().add(amount));
                        userFundSum.setAvailablebalance(userFundSum.getAvailablebalance().add(amount));

                        //修改钱包
                        os.setChannelbalance(os.getChannelbalance().add(amount));//amount
                        os.setAvailablebalance(os.getAvailablebalance().add(amount));
                        os.setUpdatedAt(date);
                        break;
                }

                OrdersEntity order = new OrdersEntity();
                String uuid = uniqId().substring(0, 16);
                order.setEntry(uuid);
                order.setLotteryId(project.getLotteryId());
                order.setMethodId(project.getMethodId());
                order.setTaskId(project.getTaskId());
                order.setProjectId(project.getProjectId());
                order.setFromuserId(project.getUserId());
                order.setOrderTypeId(8);
                order.setIssue(project.getIssue());
                order.setTitle(titleAndDescription);
                order.setAmount(amount);
                order.setDescription(titleAndDescription);
                order.setPreBalance(preChannelBalance);     //账变前 -帐变前频道资金
                order.setPreHold(preHoldBalance);           //账变前 -帐变前冻结资金
                order.setPreAvailable(preAvailableBalance); //账变前 -帐变前可用资金

                order.setChannelBalance(channelBalance);        //账变后 -帐变后可用资金
                order.setHoldBalance(holdBalance);              //账变后 -帐变后的冻结资金
                order.setAvailableBalance(availableBalance);    //账变后 -帐变后频道资金

                order.setUniqueKey(String.valueOf(System.currentTimeMillis()));
                order.setModes(project.getModes());
                order.setCreatedAt(date);
                order.setUpdatedAt(date);
                order.setActionTime(date);

                ordersList.add(order);
                betInfos.add(project);
                userFundMap.compute(project.getUserId(), (k, oldVal) -> os);
                userFundSunMap.compute(project.getUserId(), (k, oldVal) -> userFundSum);

            }

            //收集全部ordersList 和userFundList再做修改
            if(userFundMapper.doUpdateAddOrdersList(title,userFundMap) != userFundMap.size()){
                throw new RuntimeException("批量修改钱包失败");
            }
            //解锁钱包
            userFundMapper.doLockUserFund(title,userFundMap,OrderType,"CR_004 解锁");
            //批量改注单
            if (OrderType == 8){
                userFundMapper.doLockUserFund(title,userFundMap,4,"CR_004 解锁");
                betInfoMapper.updateIsDeduct(title,betInfos);
//                if(betInfoMapper.updateIsDeduct(title,betInfos) != betInfos.size()){
//                    throw new RuntimeException("修改注单状态失败");
//                }

            }else if (OrderType == 5){
                userFundMapper.doLockUserFund(title,userFundMap,5,"CP_003 解锁");
                betInfoMapper.updatePrizeStatus(title,betInfos);
            }
            //批量插入orders
            if(ordersMapper.addOrdersListAll(ordersList,title) != ordersList.size()){
                throw new RuntimeException("插入订单失败");
            }
            if (OrderType == 5 && (roomMaster.getUserWalletType() == 0 || roomMaster.getUserWalletType() == 1 || roomMaster.getUserWalletType() == 2 || roomMaster.getUserWalletType() == 3)){
                roomMasterMapper.createSpeculationList(roomMaster,ordersList);
            }

            if (!errorBetInfoList.isEmpty()) {
                //如果有因异常钱包锁定导致无法派奖应当在5S后再次处理
            }
            return true;
        } catch (Exception e) {
            //手动标记回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return false;

        }
    }

    /**
     * 写入各平台商的平台商奖期表
     * @param issueInfo
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateRoomsIssueInfo(IssueInfoEntity issueInfo){
        //2.取得平台商信息表中的平台商前缀
        List<RoomMasterEntity> roomMasters = roomMasterMapper.selectTitle();

        //3.把主奖期表的号码写入各平台商奖期表中
        issueInfoMapper.insertIssueToRooms(roomMasters.stream().map(RoomMasterEntity::getTitle).collect(Collectors.toList()),issueInfo);
        //todo:4.号码写入后查询该期订单（cn007_projects），通过对应订单的玩法(method)进行验派
        for (RoomMasterEntity roomMaster : roomMasters) {
            awardingProcessService.lotteryDraw(roomMaster,issueInfo);
        }
    }


    static String uniqId() {
        // 类似 uniqid：时间 + 随机
        return Long.toHexString(System.nanoTime()) + Long.toHexString(ThreadLocalRandom.current().nextLong());
    }
}
