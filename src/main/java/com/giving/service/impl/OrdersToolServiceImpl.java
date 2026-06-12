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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ObjectUtils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 修改注单状态，修改厅主奖期信息，
 */
@Slf4j
@Service
public class OrdersToolServiceImpl implements OrdersToolService {

    private static final AtomicLong LAST_MS = new AtomicLong(0);
    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static final String WALLET_REDIS_LOCK_PREFIX = "wallet:";
    private static final int WALLET_REDIS_LOCK_RETRY_TIMES = 10;
    private static final long WALLET_REDIS_LOCK_RETRY_INTERVAL_MS = 10L;
    private static final long WALLET_REDIS_LOCK_EXPIRE_SECONDS = 60L;
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
    @Autowired
    private UserDiffpointsMapper userDiffpointsMapper;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;

    //执行钱包操作 type5--0001
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Boolean getOrdersListAll(List<BetInfoEntity> projects, String title, int orderType, RoomMasterEntity roomMaster) {
        int lockedWalletType = orderType == 8 ? 4 : orderType;
        Set<String> lockedUserIds = ConcurrentHashMap.newKeySet();
        Map<String, String> walletRedisLockTokens = new ConcurrentHashMap<>();
        boolean synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive();
//        、、事务结束后的回调
        if (synchronizationActive) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        for (String userId : lockedUserIds) {
                            if (!userFundLockTxService.doLockUserFund(
                                    userId, false, lockedWalletType, "批量账变回滚自动解锁", title)) {
                                log.error("批量账变回滚后钱包解锁失败，厅主表名={}，账变类型={}，用户ID={}", title, orderType, userId);
                            }
                        }
                    }
                    releaseWalletRedisLocks(title, walletRedisLockTokens);
                }
            });
        }
        try {
            if (projects == null || projects.isEmpty()) {
                return true;
            }
/*            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String nowString = sdf.format(new Date());
            Map<String, Object> map = new HashMap<>();
            //统计盈利率数据
            if(orderType == 8){
                Object o = redisUtils.get(RedisKeyEnums.C_PROFIT_DATA.key);
                if(o == null){
                    map.put(nowString+"_price",BigDecimal.ZERO);
                    map.put(nowString+"_bonus",BigDecimal.ZERO);
                    redisUtils.set(RedisKeyEnums.C_PROFIT_DATA.key, JSONUtils.toJSONString(map));
                }else{
                    map = JSON.parseObject(o.toString(),Map.class);
                }
            }*/


            int i = 0;
            int redisLockDeferredOrderCount = 0;
            Set<String> redisLockFailedUserIds = new HashSet<>();
            while(i < 5){
                List<OrdersEntity> ordersList = new ArrayList<>();  //需要新增的orders
                List<BetInfoEntity> betInfos = new ArrayList<>();   //需要修改的project
                Map<String, UserFundEntity> userFundMap = new HashMap<>();
                Map<String, UserFundEntity> userFundSunMap = new HashMap<>(); //需要更新的钱包

                List<BetInfoEntity> errorBetInfoList = new ArrayList<>();
                for (BetInfoEntity project : projects) {
                    String userId = project.getUserId();
                    if (redisLockFailedUserIds.contains(userId)) {
                        redisLockDeferredOrderCount++;
                        continue;
                    }
                    //钱包锁定--Redis
                    if (!walletRedisLockTokens.containsKey(userId)) {
                        String lockToken = tryLockUserWallet(title, userId);
                        if (lockToken == null) {
                            redisLockFailedUserIds.add(userId);
                            redisLockDeferredOrderCount++;
                            log.warn("用户钱包Redis锁获取失败，本轮不处理该用户订单，厅主表名={}，用户ID={}，账变类型={}",
                                    title, userId, orderType);
                            continue;
                        }
                        walletRedisLockTokens.put(userId, lockToken);
                    }
                    BetInfoEntity currentProject = betInfoMapper.selectProjectByIdForUpdate(
                            title, project.getProjectId());
                    if (currentProject == null) {
                        throw new IllegalStateException("未查询到订单，厅主表名：" + title
                                + "，订单ID：" + project.getProjectId());
                    }
                    if (!isOrderPending(currentProject, orderType)) {
                        log.info("订单当前状态不允许重复操作钱包，本次跳过，厅主表名={}，账变类型={}，用户ID={}，订单ID={}，中奖状态={}，派奖状态={}，结算状态={}，返点状态={}，撤单状态={}",
                                title, orderType, userId, project.getProjectId(),
                                currentProject.getIsGetprize(), currentProject.getPrizeStatus(),
                                currentProject.getIsDeduct(), currentProject.getPointStatus(),
                                currentProject.getIsCancel());
                        continue;
                    }
                    if (orderType == 5 && project.getBonus() != null) {
                        currentProject.setBonus(project.getBonus());
                    }
                    project = currentProject;
                    UserFundEntity userFundSum;
                    UserFundEntity os;
                    if (userFundMap.containsKey(userId) && userFundSunMap.containsKey(userId)) {
                        //存在
                        userFundSum = userFundSunMap.get(userId);
                        os = userFundMap.get(userId);
                    }
                    else {
                        UserFundEntity o = new UserFundEntity();
                        o.setUserid(userId);
                        o.setWalletType(orderType);  //orderType=4锁定用户钱包4
                        if (orderType == 8) {
                            o.setWalletType(4);
                        }
                        os = userFundMapper.selectByUserAndTypeOne(title, o); //频道钱包
                        String lockAction = orderType==5?"CP_001":"CR_001";  // orderType=4,8 锁定用户钱包 CR_001
                        if (!userFundLockTxService.doLockUserFund(userId, true, o.getWalletType(), lockAction, title)) {
                            UserFundEntity currentWallet = userFundMapper.selectByUserAndTypeOne(title, o);
                            log.warn("用户钱包锁定失败，厅主表名={}，用户ID={}，钱包类型={}，当前锁状态={}，当前锁动作={}",
                                    title, userId, o.getWalletType(),
                                    currentWallet == null ? null : currentWallet.getIslocked(),
                                    currentWallet == null ? null : currentWallet.getLockAction());
                            errorBetInfoList.add(project);
                            continue;
                        }
                        lockedUserIds.add(userId);
                        userFundSum = userFundMapper.selectByUserSum(title, userId); //钱包全
                        os = userFundMapper.selectByUserAndTypeOne(title, o); //锁定后重新读取钱包
                        if (ObjectUtils.isEmpty(userFundSum) || ObjectUtils.isEmpty(os)) {
                            throw new IllegalStateException("未查询到用户钱包，厅主表名：" + title
                                    + "，用户ID：" + userId
                                    + "，钱包类型：" + o.getWalletType());
                        }
                        userFundMap.putIfAbsent(userId,os);
                        userFundSunMap.putIfAbsent(userId, userFundSum);
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

                            /*String t = map.get(nowString+"_price").toString();
                            BigDecimal p = new BigDecimal(t);
                            Double price = project.getTotalPrice() - Double.parseDouble(project.getUserPoint());
                            //总投注
                            map.put(nowString+"_price",p.add(BigDecimal.valueOf(price)));

                            if(project.getBonus() != 0 ){
                                BigDecimal b = new BigDecimal(map.get(nowString+"_bonus").toString());
                                //总派奖
                                map.put(nowString+"_bonus",b.add(BigDecimal.valueOf(project.getBonus())));
                            }*/

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
                            amount = BigDecimal.valueOf(Long.parseLong(project.getUserPoint()));
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
                    userFundMap.put(userId, os);
                    userFundSunMap.put(userId, userFundSum);

                }

                if (betInfos.isEmpty() && !errorBetInfoList.isEmpty()) {
                    i++;
                    if (i >= 5) {
                        throw new RuntimeException("多次重试后仍无法锁定用户钱包，剩余订单数："
                                + errorBetInfoList.size());
                    }
                    Thread.sleep(5000);
                    projects = new ArrayList<>(errorBetInfoList);
                    continue;
                }

                if (betInfos.isEmpty()) {
                    break;
                }

                //收集全部ordersList 和userFundList再做修改
                int updatedWalletCount = userFundMapper.doUpdateAddOrdersList(title,userFundMap);
                if(updatedWalletCount != userFundMap.size()){
                    throw new RuntimeException("批量修改钱包失败，应更新：" + userFundMap.size()
                            + "，实际更新：" + updatedWalletCount);
                }

                //批量改注单
                if (orderType == 8){
                    userFundMapper.doLockUserFund(title,userFundMap,4,"CR_004 解锁");
//                    betInfoMapper.updateIsDeduct(title,betInfos);
                    int updatedProjectCount = betInfoMapper.updateIsDeduct(title,betInfos);
                    if(updatedProjectCount != betInfos.size()){
                        throw new RuntimeException("修改结算状态失败，应更新：" + betInfos.size()
                                + "，实际更新：" + updatedProjectCount);
                    }

                }else if (orderType == 5){
                    userFundMapper.doLockUserFund(title,userFundMap,5,"CP_003 解锁");
//                    betInfoMapper.updatePrizeStatus(title,betInfos);
                    int updatedProjectCount = betInfoMapper.updatePrizeStatus(title,betInfos);
                    if(updatedProjectCount != betInfos.size()){
                        throw new RuntimeException("修改派奖状态失败，应更新：" + betInfos.size()
                                + "，实际更新：" + updatedProjectCount);
                    }
                }
                else if(orderType == 4){
                    userFundMapper.doLockUserFund(title,userFundMap,4,"CR_004 解锁");
                    //成功後更改返點狀態
                    betInfoMapper.updatePoint(title,betInfos);
                    userDiffpointsMapper.updateThreshold(title,betInfos);
                }


                //批量插入orders
                int insertedOrderCount = ordersMapper.addOrdersListAll(ordersList,title);
                if(insertedOrderCount != ordersList.size()){
                    throw new RuntimeException("插入账变失败，应插入：" + ordersList.size()
                            + "，实际插入：" + insertedOrderCount);
                }
                if (orderType == 5 && (roomMaster.getUserWalletType() == 0 || roomMaster.getUserWalletType() == 1 || roomMaster.getUserWalletType() == 2 || roomMaster.getUserWalletType() == 3)){
                    roomMasterMapper.createSpeculationList(roomMaster,ordersList);
                }
                /*if(orderType == 8){
                    redisUtils.set(RedisKeyEnums.C_PROFIT_DATA.key, JSONUtils.toJSONString(map));
                }*/


                if (!errorBetInfoList.isEmpty()) {
                    //如果有因异常钱包锁定导致无法派奖应当在5S后再次处理
                    i++;
                    if (i >= 5) {
                        throw new RuntimeException("多次重试后仍无法锁定用户钱包，剩余订单数："
                                + errorBetInfoList.size());
                    }
                    Thread.sleep(5000);
                    projects = new ArrayList<>();
                    projects.addAll(errorBetInfoList);
                }else{
                    break;
                }
            }
            if (redisLockDeferredOrderCount > 0) {
                log.warn("用户钱包Redis锁连续尝试{}次仍未获取，本次订单保持原状态，厅主表名={}，账变类型={}，跳过用户数={}，跳过订单数={}",
                        WALLET_REDIS_LOCK_RETRY_TIMES, title, orderType,
                        redisLockFailedUserIds.size(), redisLockDeferredOrderCount);
                return false;
            }
            return true;

        } catch (Exception e) {
            String firstProjectId = projects == null || projects.isEmpty()
                    ? null : projects.get(0).getProjectId();
            String lastProjectId = projects == null || projects.isEmpty()
                    ? null : projects.get(projects.size() - 1).getProjectId();
            String rootCauseMessage = getRootCauseMessage(e);
            log.error("批量账变失败，厅主表名={}，账变类型={}，订单数={}，首笔订单ID={}，末笔订单ID={}",
                    title, orderType, projects == null ? 0 : projects.size(),
                    firstProjectId, lastProjectId, e);
            throw new IllegalStateException("批量账变失败，具体原因：" + rootCauseMessage
                    + "，首笔订单ID：" + firstProjectId
                    + "，末笔订单ID：" + lastProjectId, e);
        } finally {
            if (!synchronizationActive) {
                releaseWalletRedisLocks(title, walletRedisLockTokens);
            }
        }
    }

    private String tryLockUserWallet(String title, String userId) {
        String lockKey = getWalletRedisLockKey(title, userId);
        String lockToken = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= WALLET_REDIS_LOCK_RETRY_TIMES; attempt++) {
            if (redisUtils.setLock(lockKey, lockToken, WALLET_REDIS_LOCK_EXPIRE_SECONDS)) {
                return lockToken;
            }
            if (attempt < WALLET_REDIS_LOCK_RETRY_TIMES) {
                try {
                    Thread.sleep(WALLET_REDIS_LOCK_RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("等待用户钱包Redis锁时线程被中断，厅主表名={}，用户ID={}", title, userId);
                    return null;
                }
            }
        }
        return null;
    }

    private void releaseWalletRedisLocks(String title, Map<String, String> walletRedisLockTokens) {
        walletRedisLockTokens.forEach((userId, lockToken) -> {
            String lockKey = getWalletRedisLockKey(title, userId);
            if (!redisUtils.unlock(lockKey, lockToken)) {
                log.warn("用户钱包Redis锁未释放或已过期，厅主表名={}，用户ID={}", title, userId);
            }
        });
        walletRedisLockTokens.clear();
    }

    private String getWalletRedisLockKey(String title, String userId) {
        return WALLET_REDIS_LOCK_PREFIX + title + ":" + userId;
    }

    private boolean isOrderPending(BetInfoEntity project, int orderType) {
        if (project.getIsCancel() != null && project.getIsCancel() != 0) {
            return false;
        }
        if (orderType == 5) {
            return Integer.valueOf(0).equals(project.getPrizeStatus())
                    && !Integer.valueOf(2).equals(project.getIsGetprize());
        }
        if (orderType == 8) {
            return Integer.valueOf(0).equals(project.getIsDeduct());
        }
        if (orderType == 4) {
            return Integer.valueOf(0).equals(project.getPointStatus());
        }
        return true;
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        return message == null || message.trim().isEmpty()
                ? rootCause.getClass().getSimpleName()
                : message;
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
        // 厅组奖期提交后再启动验奖，避免首次录号读取到未提交状态。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (RoomMasterEntity roomMaster : roomMasters) {
                    awardingProcessService.lotteryDraw(roomMaster, issueInfo);
                }
            }
        });
    }

    @Override
    public Boolean updateIssueDeduct(TempIssueInfoEntity Issue,String title){
        if (tempIssueInfoMapper.updateByTitleStatusDeduct(title, Issue) != 1) {
            throw new RuntimeException("修改奖期为真实扣款状态失败");
        }
        return true;
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
