package com.giving.service.impl;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.management.DateSourceManagement;
import com.giving.mapper.IssueHistoryMapper;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.LotteryMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.DrawSourceReq;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import com.giving.service.OrdersToolService;
import com.giving.util.RedisUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;


/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:47
 */
@Slf4j
@Service
public class AwardingProcessServiceImpl implements AwardingProcessService {
    private static final String AWARD_TITLE_LOCK_PREFIX = "award:process:title:";
    private static final String AWARD_TITLE_QUEUE_PREFIX = "award:process:title:queue:";
    private static final String AWARD_TITLE_QUEUE_TITLES_KEY = "award:process:title:queue:titles";
    private static final long AWARD_TITLE_LOCK_EXPIRE_SECONDS = 3600L;
    private static final long AWARD_TITLE_LOCK_RETRY_INTERVAL_MS = 100L;
    private static final long AWARD_TITLE_QUEUE_RETRY_DELAY_MS = 5000L;
    private static final Map<String, ExecutorService> AWARD_TITLE_EXECUTORS = new ConcurrentHashMap<>();
    private static final Set<String> AWARD_TITLE_DRAINING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @Autowired
    IssueInfoMapper issueInfoMapper;

    @Autowired
    AwardGivingService awardGivingService;

    @Autowired
    LotteryMapper lotteryMapper;

    @Autowired
    RoomMasterMapper roomMasterMapper;

    @Autowired
    AwardGivingService awardService;

    @Autowired
    OrdersToolService ordersToolService;

    @Autowired
    private IssueHistoryMapper issueHistoryMapper;
    @Autowired
    private RedisUtils redisUtils;

    @PostConstruct
    public void initAwardTitleQueue() {
        recoverAwardTitleQueue();
    }

    /**
     * 派奖流程
     * @param req
     */
    @Override
    public ApiResp<String> drawSource(DrawSourceReq req) {
        //判断是否存在奖期
        LambdaQueryWrapper<IssueInfoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IssueInfoEntity::getLotteryId,req.getLotteryId());
        queryWrapper.eq(IssueInfoEntity::getIssue,req.getIssue());
        IssueInfoEntity issueInfo = issueInfoMapper.selectOne(queryWrapper);
        //如果存在就修改奖期
        if(ObjectUtils.isEmpty(issueInfo)){
            log.info("彩种{}========奖期不存在==========={}",req.getLotteryId(), req.getIssue());
            return ApiResp.paramError("奖期不存在"+req.getIssue());
        }
        if(!StringUtils.isEmpty(issueInfo.getCode())) {
            log.info("彩种{}========开奖号码已存在，进行厅组验派==========={}",req.getLotteryId(),req.getIssue());
            ordersToolService.updateRoomsIssueInfo(issueInfo);
//            return ApiResp.paramError("开奖号码已存在，已经录号"+req.getIssue());
        }
        //修改奖期
        issueInfo.setCode(req.getWinCode());
        issueInfo.setWriteTime(new Date());
        issueInfo.setStatusFetch(2);
        issueInfo.setStatusCode(2);
        issueInfo.setWriteId(0);
        try {
            DateSourceManagement.use("gs");
            issueInfoMapper.updateById(issueInfo);
        } finally {
            DateSourceManagement.clear();
        }

        //奖期历史记录
        if (req.getLotteryId() == 130 || req.getLotteryId() == 132 || req.getLotteryId() == 281) {
            req.setWinCode(req.getWinCode().replace(",",""));
        }
        //存入历史奖期
        issueHistoryMapper.updateOrInsert(req,issueInfo);

        ordersToolService.updateRoomsIssueInfo(issueInfo);

        return ApiResp.sucess();
    }



    /**
     *根据采种方法执行对应的验证流程
     * @param roomMaster
     * @param issueInfo
     */
    @Override
    public void lotteryDraw(RoomMasterEntity roomMaster,IssueInfoEntity issueInfo){
        AwardTitleQueueTask task = buildAwardTitleQueueTask(roomMaster, issueInfo);
        enqueueAwardTitleTask(task);
    }

    private AwardTitleQueueTask buildAwardTitleQueueTask(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo) {
        if (ObjectUtils.isEmpty(roomMaster) || StringUtils.isEmpty(roomMaster.getTitle())) {
            throw new IllegalArgumentException("roomMaster title can not be empty");
        }
        if (ObjectUtils.isEmpty(issueInfo) || StringUtils.isEmpty(issueInfo.getIssue())
                || ObjectUtils.isEmpty(issueInfo.getLotteryId())) {
            throw new IllegalArgumentException("issue info can not be empty");
        }
        AwardTitleQueueTask task = new AwardTitleQueueTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTitle(roomMaster.getTitle());
        task.setMasterId(roomMaster.getMasterId());
        task.setLotteryId(issueInfo.getLotteryId());
        task.setIssue(issueInfo.getIssue());
        task.setCode(issueInfo.getCode());
        task.setCreatedAt(System.currentTimeMillis());
        return task;
    }

    private void enqueueAwardTitleTask(AwardTitleQueueTask task) {
        String queueKey = getAwardTitleQueueKey(task.getTitle());
        String payload = JSON.toJSONString(task);
        redisUtils.sSet(AWARD_TITLE_QUEUE_TITLES_KEY, task.getTitle());
        if (!redisUtils.lSet(queueKey, payload)) {
            throw new IllegalStateException("failed to enqueue award title task, title=" + task.getTitle()
                    + ", lotteryId=" + task.getLotteryId() + ", issue=" + task.getIssue());
        }
        log.info("后台验奖派奖已写入title持久队列，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}，queueSize={}",
                task.getTaskId(), task.getTitle(), task.getMasterId(), task.getLotteryId(), task.getIssue(),
                redisUtils.lGetListSize(queueKey));
        triggerAwardTitleQueue(task.getTitle());
    }

    private void recoverAwardTitleQueue() {
        Set<String> titles = new HashSet<>();
        try {
            Set<Object> redisTitles = redisUtils.sGet(AWARD_TITLE_QUEUE_TITLES_KEY);
            if (redisTitles != null) {
                for (Object title : redisTitles) {
                    if (title != null && !StringUtils.isEmpty(String.valueOf(title))) {
                        titles.add(String.valueOf(title));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取后台验派title队列集合失败，启动恢复继续使用厅组列表", e);
        }

        try {
            List<RoomMasterEntity> roomMasters = roomMasterMapper.selectTitle();
            if (roomMasters != null) {
                for (RoomMasterEntity roomMaster : roomMasters) {
                    if (!ObjectUtils.isEmpty(roomMaster) && !StringUtils.isEmpty(roomMaster.getTitle())) {
                        titles.add(roomMaster.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取厅组列表恢复后台验派title队列失败", e);
        }

        for (String title : titles) {
            long queueSize = redisUtils.lGetListSize(getAwardTitleQueueKey(title));
            if (queueSize > 0) {
                log.info("发现未完成后台验派title队列，准备恢复处理，title={}，queueSize={}", title, queueSize);
                triggerAwardTitleQueue(title);
            }
        }
    }

    private void triggerAwardTitleQueue(String title) {
        if (StringUtils.isEmpty(title)) {
            return;
        }
        if (!AWARD_TITLE_DRAINING.add(title)) {
            return;
        }
        try {
            getAwardTitleExecutor(title).submit(() -> {
                try {
                    drainAwardTitleQueue(title);
                } finally {
                    AWARD_TITLE_DRAINING.remove(title);
                    if (redisUtils.lGetListSize(getAwardTitleQueueKey(title)) > 0) {
                        triggerAwardTitleQueue(title);
                    }
                }
            });
        } catch (RuntimeException e) {
            AWARD_TITLE_DRAINING.remove(title);
            throw e;
        }
    }

    private void drainAwardTitleQueue(String title) {
        String queueKey = getAwardTitleQueueKey(title);
        while (redisUtils.lGetListSize(queueKey) > 0) {
            String titleLockKey = getAwardTitleLockKey(title);
            String titleLockToken = UUID.randomUUID().toString();
            boolean titleLockAcquired = false;
            String payload = null;
            try {
                waitForAwardTitleLock(titleLockKey, titleLockToken, title);
                titleLockAcquired = true;
                Object queuedValue = redisUtils.lGetIndex(queueKey, 0);
                if (queuedValue == null) {
                    return;
                }
                payload = String.valueOf(queuedValue);
                AwardTitleQueueTask task;
                try {
                    task = JSON.parseObject(payload, AwardTitleQueueTask.class);
                } catch (Exception parseException) {
                    log.warn("后台验派title队列任务JSON解析失败，已移除，title={}，payload={}", title, payload, parseException);
                    redisUtils.lRemove(queueKey, 1, payload);
                    continue;
                }
                if (task == null || StringUtils.isEmpty(task.getTitle()) || StringUtils.isEmpty(task.getIssue())
                        || ObjectUtils.isEmpty(task.getLotteryId())) {
                    log.warn("后台验派title队列任务数据无效，已移除，title={}，payload={}", title, payload);
                    redisUtils.lRemove(queueKey, 1, payload);
                    continue;
                }

                processAwardTitleTask(task);
                long removed = redisUtils.lRemove(queueKey, 1, payload);
                if (removed <= 0) {
                    log.warn("后台验派title队列任务ACK失败，可能会被重复处理，taskId={}，title={}，彩种ID={}，奖期={}",
                            task.getTaskId(), task.getTitle(), task.getLotteryId(), task.getIssue());
                }
            } catch (Exception e) {
                log.error("后台验派title队列任务执行失败，保留队列等待重试，title={}，payload={}", title, payload, e);
                sleepBeforeAwardTitleQueueRetry();
                return;
            } finally {
                if (titleLockAcquired && !redisUtils.unlock(titleLockKey, titleLockToken)) {
                    log.warn("后台验派title锁未释放或已过期，title={}", title);
                }
            }
        }
    }

    private void processAwardTitleTask(AwardTitleQueueTask task) {
        RoomMasterEntity roomMaster = loadRoomMaster(task);
        if (ObjectUtils.isEmpty(roomMaster)) {
            log.warn("后台验派title队列任务找不到厅组，已跳过，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    task.getTaskId(), task.getTitle(), task.getMasterId(), task.getLotteryId(), task.getIssue());
            return;
        }
        IssueInfoEntity issueInfo = new IssueInfoEntity();
        issueInfo.setLotteryId(task.getLotteryId());
        issueInfo.setIssue(task.getIssue());
        issueInfo.setCode(task.getCode());
        LotteryEntity lottery = lotteryMapper.selectById(task.getLotteryId());
        executeLotteryDraw(roomMaster, issueInfo, lottery, task.getTaskId());
    }

    private RoomMasterEntity loadRoomMaster(AwardTitleQueueTask task) {
        if (task.getMasterId() != null) {
            LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoomMasterEntity::getMasterId, task.getMasterId());
            RoomMasterEntity roomMaster = roomMasterMapper.selectOne(wrapper);
            if (!ObjectUtils.isEmpty(roomMaster)) {
                return roomMaster;
            }
        }
        return null;
    }

    private void executeLotteryDraw(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo, LotteryEntity lottery, String taskId) {
        try {
            if (ObjectUtils.isEmpty(lottery)) {
                log.warn("后台验奖派奖跳过，彩种不存在，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                        taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
                return;
            }
            log.info("后台验奖派奖开始，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());

                NoticeReq n = new NoticeReq();
                n.setRoomMaster(roomMaster);
                n.setTableName(roomMaster.getTitle()+"_issue_info");
                n.setTitle(roomMaster.getTitle());
                n.setIssue(issueInfo.getIssue());
                n.setCode(issueInfo.getCode());
                n.setLotteryId(issueInfo.getLotteryId());
                switch (lottery.getFunctionType()){
                    case "VN_S":
                    case "VN_C": //18--越南自开
                        awardGivingService.notice(n);
                        break;
                    case "VN_N"://28-组 越南北部
                        awardGivingService.noticeNorth(n);
                        break;
                    case "TH":      //泰国彩
                    case "TH_30S":  //泰国分分彩
                        awardGivingService.noticeTh(n);
                        break;
                    case "LA":  //老挝彩
                    case "MY":  //马来西亚彩
                        awardGivingService.noticeLw(n);
                        break;
//                  case "FC3D":  //加拿大1分3D  亚洲30秒3D
                    case "K3":  //亚洲30秒快3 亚洲1分快3  澳洲5分快3
                        awardGivingService.noticeKs(n);
                        break;
                default:
                    log.warn("unsupported lottery function type, title={}，厅主ID={}，彩种ID={}，奖期={}，functionType={}",
                            roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(),
                            issueInfo.getIssue(), lottery.getFunctionType());
                    break;
            }
            log.info("后台验奖派奖完成，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
        } catch (Exception e) {
            log.error("后台验奖派奖失败，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue(), e);
            throw new RuntimeException(e);
        }
    }

    private ExecutorService getAwardTitleExecutor(String title) {
        return AWARD_TITLE_EXECUTORS.computeIfAbsent(title, key ->
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("award-title-" + key);
                    return thread;
                }));
    }

    private void waitForAwardTitleLock(String titleLockKey, String titleLockToken, String title) {
        long startTime = System.currentTimeMillis();
        boolean waitLogged = false;
        while (true) {
            if (redisUtils.setLock(titleLockKey, titleLockToken, AWARD_TITLE_LOCK_EXPIRE_SECONDS)) {
                long waitMs = System.currentTimeMillis() - startTime;
                if (waitLogged) {
                    log.info("后台验派title锁等待完成，title={}，waitMs={}", title, waitMs);
                }
                return;
            }
            if (!waitLogged) {
                waitLogged = true;
                log.info("后台验派title锁被占用，进入排队等待，title={}", title);
            }
            try {
                Thread.sleep(AWARD_TITLE_LOCK_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for award title lock", e);
            }
        }
    }

    private void sleepBeforeAwardTitleQueueRetry() {
        try {
            Thread.sleep(AWARD_TITLE_QUEUE_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getAwardTitleLockKey(String title) {
        return AWARD_TITLE_LOCK_PREFIX + title;
    }

    private String getAwardTitleQueueKey(String title) {
        return AWARD_TITLE_QUEUE_PREFIX + title;
    }

    @Data
    public static class AwardTitleQueueTask {
        private String taskId;
        private String title;
        private Integer masterId;
        private Long lotteryId;
        private String issue;
        private String code;
        private Long createdAt;
    }

    //测试数据生成
    private void FakeIssue(IssueInfoEntity issueInfo) {
        new Thread(() -> {
            LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
            if (!lottery.getFunctionType().equals("K3")) {
                awardService.createData(Integer.parseInt(issueInfo.getLotteryId().toString()));
            }
        }).start();
    }
}
