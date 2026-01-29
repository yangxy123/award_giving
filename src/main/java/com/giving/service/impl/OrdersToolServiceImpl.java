package com.giving.service.impl;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.giving.entity.*;
import com.giving.enums.RedisKeyEnums;
import com.giving.mapper.*;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import com.giving.service.OrdersToolService;
import com.giving.service.UserFundLockTxService;
import com.giving.util.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 修改注单状态，修改厅主奖期信息，
 */
@Service
public class OrdersToolServiceImpl implements OrdersToolService {

    private static final AtomicLong LAST_MS = new AtomicLong(0);
    private static final AtomicInteger SEQ = new AtomicInteger(0);
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
    @Autowired
    private RedisUtils redisUtils;


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Boolean getOrdersListAll(List<BetInfoEntity> projects, String title, int orderType, RoomMasterEntity roomMaster) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String nowString = sdf.format(new Date());
            String str = redisUtils.get(RedisKeyEnums.C_PROFIT_DATA.key).toString();
            Map<String, BigDecimal> map = new HashMap<>();
            if(str == null || str.equals("")){
                map.put(nowString+"_price",BigDecimal.ZERO);
                map.put(nowString+"_bonus",BigDecimal.ZERO);
                redisUtils.set(RedisKeyEnums.C_PROFIT_DATA.key, JSONUtils.toJSONString(map));
            }else{
                map = JSON.parseObject(str,Map.class);
            }


            int i = 0;
            while(i < 5){
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
                        o.setWalletType(orderType);  //orderType=4锁定用户钱包4
                        if (orderType == 8) {
                            o.setWalletType(4);
                        }
                        os = userFundMapper.selectByUserAndTypeOne(title, o); //频道钱包
                        userFundMap.putIfAbsent(project.getUserId(),os);
                        userFundSunMap.putIfAbsent(project.getUserId(), userFundSum);
                        String lockAction = orderType==5?"CP_001":"CR_001";  // orderType=4,8 锁定用户钱包 CR_001
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
                    switch (orderType) {
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

                            BigDecimal p = map.get(nowString+"_price");
                            Double price = project.getTotalPrice() - Double.parseDouble(project.getUserPoint());
                            //总投注
                            map.put(nowString+"_price",p.add(BigDecimal.valueOf(price)));

                            if(project.getBonus() != 0 ){
                                BigDecimal b = map.get(nowString+"_bonus");
                                //总派奖
                                map.put(nowString+"_bonus",b.add(BigDecimal.valueOf(project.getBonus())));
                            }

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
                        case 4: //返点派送 -- 派奖时 wallet_type 5 + channelbalance  and availablebalance
                            amount = BigDecimal.valueOf(project.getBonus());
                            availableBalance = preAvailableBalance.add(amount);
                            channelBalance = availableBalance.add(amount);
                            titleAndDescription = "返点派送";
                            //已经返点派送
                            project.setPointStatus(1);
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
                    String uuid =uniqId16();
                    order.setEntry(uuid);
                    order.setLotteryId(project.getLotteryId());
                    order.setMethodId(project.getMethodId());
                    order.setTaskId(project.getTaskId());
                    order.setProjectId(project.getProjectId());
                    order.setFromuserId(project.getUserId());
                    order.setOrderTypeId(orderType);
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

                //批量改注单
                if (orderType == 8){
                    userFundMapper.doLockUserFund(title,userFundMap,4,"CR_004 解锁");
//                    betInfoMapper.updateIsDeduct(title,betInfos);
                if(betInfoMapper.updateIsDeduct(title,betInfos) != betInfos.size()){
                    throw new RuntimeException("修改注单状态失败");
                }

                }else if (orderType == 5){
                    userFundMapper.doLockUserFund(title,userFundMap,5,"CP_003 解锁");
//                    betInfoMapper.updatePrizeStatus(title,betInfos);
                    if(betInfoMapper.updatePrizeStatus(title,betInfos) != betInfos.size()){
                        throw new RuntimeException("修改注单状态失败");
                    }
                }

                //批量插入orders
                if(ordersMapper.addOrdersListAll(ordersList,title) != ordersList.size()){
                    throw new RuntimeException("插入订单失败");
                }
                if (orderType == 5 && (roomMaster.getUserWalletType() == 0 || roomMaster.getUserWalletType() == 1 || roomMaster.getUserWalletType() == 2 || roomMaster.getUserWalletType() == 3)){
                    roomMasterMapper.createSpeculationList(roomMaster,ordersList);
                }
                redisUtils.set(RedisKeyEnums.C_PROFIT_DATA.key, JSONUtils.toJSONString(map));
                if (!errorBetInfoList.isEmpty()) {
                    //如果有因异常钱包锁定导致无法派奖应当在5S后再次处理
                    Thread.sleep(5000);
                    projects = new ArrayList<>();
                    projects.addAll(errorBetInfoList);
                }else{
                    break;
                }
                i++;
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

    // 16位可排序ID（hex），后生成的按字符串排序一定更大（同JVM内）
    public static synchronized String uniqId16() {
        long now = System.currentTimeMillis();
        long last = LAST_MS.get();

        // 时钟回拨保护：保证不倒退
        if (now < last) now = last;

        int seq;
        if (now == last) {
            seq = SEQ.incrementAndGet();
            if (seq > 0xFFFF) { // 同一毫秒超过65535个，等下一毫秒
                do { now = System.currentTimeMillis(); } while (now <= last);
                LAST_MS.set(now);
                SEQ.set(0);
                seq = 0;
            }
        } else {
            LAST_MS.set(now);
            SEQ.set(0);
            seq = 0;
        }

        long id = (now << 16) | (seq & 0xFFFFL);
        return String.format("%016x", id); // 固定16位，字典序可排序
    }
}
