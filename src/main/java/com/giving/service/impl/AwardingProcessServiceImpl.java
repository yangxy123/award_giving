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
 * 验奖派奖流程实现类。
 *
 * <p>主要职责：</p>
 * <ul>
 *     <li>录入开奖号码，并同步更新奖期状态。</li>
 *     <li>把需要按厅组 title 执行的派奖任务写入 Redis 持久队列。</li>
 *     <li>按 title 串行消费派奖任务，防止同一个厅组并发派奖导致余额、订单、奖期数据冲突。</li>
 *     <li>项目启动时恢复 Redis 中未处理完成的派奖任务。</li>
 * </ul>
 *
 * <p>重要说明：</p>
 * <ul>
 *     <li>同一个 title 下的任务是单线程串行处理。</li>
 *     <li>如果队首任务一直失败，后面的任务会一直等待。</li>
 *     <li>失败任务当前会保留在队列中等待重试，避免任务丢失，但也可能造成队首阻塞。</li>
 * </ul>
 *
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:47
 */
@Slf4j
@Service
public class AwardingProcessServiceImpl implements AwardingProcessService {

    /**
     * Redis 锁 key 前缀。
     *
     * <p>最终 key 示例：</p>
     * <pre>
     * award:process:title:cn0003
     * </pre>
     *
     * <p>用途：同一个 title 同一时间只允许一个派奖消费任务执行。</p>
     */
    private static final String AWARD_TITLE_LOCK_PREFIX = "award:process:title:";

    /**
     * Redis 队列 key 前缀。
     *
     * <p>最终 key 示例：</p>
     * <pre>
     * award:process:title:queue:cn0003
     * </pre>
     *
     * <p>用途：按 title 存放派奖任务 JSON。</p>
     */
    private static final String AWARD_TITLE_QUEUE_PREFIX = "award:process:title:queue:";

    /**
     * Redis Set key。
     *
     * <p>用于记录所有出现过派奖队列的 title，服务重启时可根据这个集合恢复未处理任务。</p>
     */
    private static final String AWARD_TITLE_QUEUE_TITLES_KEY = "award:process:title:queue:titles";

    /**
     * title 锁过期时间，单位：秒。
     *
     * <p>这里设置为 3600 秒，即 1 小时。</p>
     * <p>如果任务执行时间超过 1 小时，锁可能过期，finally 中释放锁时会打印“锁未释放或已过期”。</p>
     */
    private static final long AWARD_TITLE_LOCK_EXPIRE_SECONDS = 3600L;

    /**
     * 获取 title 锁失败后的重试间隔，单位：毫秒。
     *
     * <p>当前为 100ms，即锁被占用时每 100ms 尝试获取一次。</p>
     */
    private static final long AWARD_TITLE_LOCK_RETRY_INTERVAL_MS = 100L;

    /**
     * 队列任务执行失败后的等待时间，单位：毫秒。
     *
     * <p>当前为 5000ms，即失败后睡眠 5 秒再退出当前消费流程。</p>
     * <p>外层 finally 会看到队列仍有数据，然后再次触发消费。</p>
     */
    private static final long AWARD_TITLE_QUEUE_RETRY_DELAY_MS = 5000L;

    /**
     * 每个 title 对应一个单线程执行器。
     *
     * <p>key：title。</p>
     * <p>value：单线程 ExecutorService。</p>
     *
     * <p>这样设计的效果是：不同 title 可以并行处理，同一个 title 串行处理。</p>
     */
    private static final Map<String, ExecutorService> AWARD_TITLE_EXECUTORS = new ConcurrentHashMap<>();

    /**
     * 当前正在消费队列的 title 集合。
     *
     * <p>作用：</p>
     * <ul>
     *     <li>防止同一个 title 重复提交多个 drainAwardTitleQueue 线程。</li>
     *     <li>如果某个 title 已经在消费，再次触发时会直接 return。</li>
     * </ul>
     *
     * <p>注意：这里是 JVM 内存级别的控制，不是 Redis 分布式锁。</p>
     * <p>真正跨进程/跨服务的互斥控制在 waitForAwardTitleLock 方法中的 Redis 锁。</p>
     */
    private static final Set<String> AWARD_TITLE_DRAINING =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * 奖期信息 Mapper。
     */
    @Autowired
    IssueInfoMapper issueInfoMapper;

    /**
     * 派奖服务。
     *
     * <p>executeLotteryDraw 中会根据彩种 functionType 调用不同的 notice 方法。</p>
     */
    @Autowired
    AwardGivingService awardGivingService;

    /**
     * 彩种 Mapper。
     */
    @Autowired
    LotteryMapper lotteryMapper;

    /**
     * 厅组 Mapper。
     */
    @Autowired
    RoomMasterMapper roomMasterMapper;

    /**
     * 派奖服务别名。
     *
     * <p>当前主要在 FakeIssue 测试数据生成方法中使用。</p>
     */
    @Autowired
    AwardGivingService awardService;

    /**
     * 订单工具服务。
     *
     * <p>用于更新厅组奖期信息。</p>
     */
    @Autowired
    OrdersToolService ordersToolService;

    /**
     * 奖期历史 Mapper。
     */
    @Autowired
    private IssueHistoryMapper issueHistoryMapper;

    /**
     * Redis 工具类。
     *
     * <p>本类中用于：</p>
     * <ul>
     *     <li>写入 Redis List 队列。</li>
     *     <li>维护 Redis Set 中的 title 集合。</li>
     *     <li>获取、释放 Redis 锁。</li>
     *     <li>恢复未处理完的队列任务。</li>
     * </ul>
     */
    @Autowired
    private RedisUtils redisUtils;

    /**
     * Bean 初始化完成后执行。
     *
     * <p>服务启动时会尝试恢复之前 Redis 中残留的派奖任务。</p>
     * <p>比如服务重启、宕机后，未 ACK 的任务仍保留在 Redis List 中，此处会重新触发消费。</p>
     */
    @PostConstruct
    public void initAwardTitleQueue() {
        recoverAwardTitleQueue();
    }

    /**
     * 派奖流程入口：录入开奖号码并触发厅组验派。
     *
     * <p>流程：</p>
     * <ol>
     *     <li>根据 lotteryId + issue 查询奖期。</li>
     *     <li>如果奖期不存在，直接返回参数错误。</li>
     *     <li>如果开奖号码已存在，则先执行厅组奖期更新。</li>
     *     <li>更新当前奖期开奖号码、写入时间、状态等。</li>
     *     <li>写入或更新奖期历史表。</li>
     *     <li>调用 ordersToolService.updateRoomsIssueInfo 触发厅组相关处理。</li>
     * </ol>
     *
     * @param req 开奖请求参数
     * @return API 响应
     */
    @Override
    public ApiResp<String> drawSource(DrawSourceReq req) {
        // 根据彩种 ID 和奖期号查询奖期记录。
        LambdaQueryWrapper<IssueInfoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IssueInfoEntity::getLotteryId, req.getLotteryId());
        queryWrapper.eq(IssueInfoEntity::getIssue, req.getIssue());
        IssueInfoEntity issueInfo = issueInfoMapper.selectOne(queryWrapper);
        // 奖期不存在时，不能继续录号或派奖。
        if (ObjectUtils.isEmpty(issueInfo)) {
            log.info("彩种{}========奖期不存在==========={}", req.getLotteryId(), req.getIssue());
            return ApiResp.paramError("奖期不存在" + req.getIssue());
        }

        // 如果开奖号码已经存在，仍然执行厅组奖期更新。
        // 原来的 return 被注释掉了，说明现在允许重复触发厅组验派逻辑。
        if (!StringUtils.isEmpty(issueInfo.getCode())) {
            log.info("彩种{}========开奖号码已存在，进行厅组验派==========={}", req.getLotteryId(), req.getIssue());
            ordersToolService.updateRoomsIssueInfo(issueInfo);
//            return ApiResp.paramError("开奖号码已存在，已经录号"+req.getIssue());
        }

        // 更新奖期开奖号码和状态。
        issueInfo.setCode(req.getWinCode());
        issueInfo.setWriteTime(new Date());
        issueInfo.setStatusFetch(2);
        issueInfo.setStatusCode(2);
        issueInfo.setWriteId(0);
        try {
            // 切换数据源到 gs。
            DateSourceManagement.use("gs");
            issueInfoMapper.updateById(issueInfo);
        } finally {
            // 清理当前线程的数据源，避免影响后续数据库操作。
            DateSourceManagement.clear();
        }

        // 部分彩种历史奖期的开奖号码格式需要去掉逗号。
        if (req.getLotteryId() == 130 || req.getLotteryId() == 132 || req.getLotteryId() == 281) {
            req.setWinCode(req.getWinCode().replace(",",""));
        }
        // 写入或更新历史奖期记录。
        issueHistoryMapper.updateOrInsert(req, issueInfo);
        // 更新所有厅组的奖期信息，并继续触发后续验派流程。
        ordersToolService.updateRoomsIssueInfo(issueInfo);

        return ApiResp.sucess();
    }



    /**
     * 根据彩种和厅组信息触发验奖派奖。
     *
     * <p>当前实现不是直接派奖，而是：</p>
     * <ol>
     *     <li>先构建 AwardTitleQueueTask 任务对象。</li>
     *     <li>再写入 Redis 持久队列。</li>
     *     <li>最后触发后台线程消费队列。</li>
     * </ol>
     *
     * <p>重点：同一个 title 的任务会排队串行执行，不会并发执行。</p>
     *
     * @param roomMaster 厅组信息
     * @param issueInfo 奖期信息
     */
    @Override
    public void lotteryDraw(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo) {
        // 1. 根据房间/盘口信息和奖期信息组装一个持久化队列任务。
        AwardTitleQueueTask task = buildAwardTitleQueueTask(roomMaster, issueInfo);

        // 2. 把任务加入 Redis 队列，后面由队列消费者线程处理。
        enqueueAwardTitleTask(task);
    }

    /**
     * 构建派奖队列任务。
     *
     * <p>这里只做参数校验和任务对象组装，不执行派奖。</p>
     *
     * @param roomMaster 厅组信息
     * @param issueInfo 奖期信息
     * @return 队列任务对象
     */
    private AwardTitleQueueTask buildAwardTitleQueueTask(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo) {
        // 校验厅组对象和 title。
        if (ObjectUtils.isEmpty(roomMaster) || StringUtils.isEmpty(roomMaster.getTitle())) {
            throw new IllegalArgumentException("roomMaster title can not be empty");
        }
        // 校验奖期对象、奖期号、彩种 ID。
        if (ObjectUtils.isEmpty(issueInfo) || StringUtils.isEmpty(issueInfo.getIssue())
                || ObjectUtils.isEmpty(issueInfo.getLotteryId())) {
            throw new IllegalArgumentException("issue info can not be empty");
        }
        // 组装任务对象。
        AwardTitleQueueTask task = new AwardTitleQueueTask();
        // 每个任务生成唯一 taskId，方便日志追踪。
        task.setTaskId(UUID.randomUUID().toString());
        // title 用于区分不同厅组队列。
        task.setTitle(roomMaster.getTitle());
        // 厅主 ID。
        task.setMasterId(roomMaster.getMasterId());
        // 彩种 ID。
        task.setLotteryId(issueInfo.getLotteryId());
        // 奖期期号。
        task.setIssue(issueInfo.getIssue());
        // 开奖号码。
        task.setCode(issueInfo.getCode());
        // 任务创建时间戳。
        task.setCreatedAt(System.currentTimeMillis());
        return task;
    }

    /**
     * 将派奖任务写入 Redis 持久队列。
     *
     * <p>流程：</p>
     * <ol>
     *     <li>根据 title 生成 Redis List 队列 key。</li>
     *     <li>把 title 写入 Redis Set，方便服务重启后恢复队列。</li>
     *     <li>把任务对象序列化成 JSON，写入 Redis List。</li>
     *     <li>写入成功后触发该 title 的队列消费。</li>
     * </ol>
     *
     * <p>注意：这里写入队列后并不代表已经派奖完成。</p>
     *
     * @param task 待执行的派奖任务
     */
    private void enqueueAwardTitleTask(AwardTitleQueueTask task) {
        // 每个 title 一个独立队列。
        String queueKey = getAwardTitleQueueKey(task.getTitle());
        // 将任务转成 JSON 存到 Redis，方便跨服务重启恢复。
        String payload = JSON.toJSONString(task);
        // 保存 title 到 Redis Set，服务重启时可扫描所有 title 的队列。
        redisUtils.sSet(AWARD_TITLE_QUEUE_TITLES_KEY, task.getTitle());
        // 写入 Redis List。如果失败，抛异常，避免任务静默丢失。
        if (!redisUtils.lSet(queueKey, payload)) {
            throw new IllegalStateException("failed to enqueue award title task, title=" + task.getTitle()
                    + ", lotteryId=" + task.getLotteryId() + ", issue=" + task.getIssue());
        }
        // 记录入队日志。
        log.info("后台验奖派奖已写入title持久队列，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}，queueSize={}",
                task.getTaskId(), task.getTitle(), task.getMasterId(), task.getLotteryId(), task.getIssue(),
                redisUtils.lGetListSize(queueKey));
        // 触发该 title 的队列消费。
        // 如果该 title 已经在消费中，triggerAwardTitleQueue 内部会直接 return。
        triggerAwardTitleQueue(task.getTitle());
    }

    /**
     * 恢复 Redis 中未处理完的派奖队列。
     *
     * <p>触发场景：</p>
     * <ul>
     *     <li>服务启动。</li>
     *     <li>应用重启后 Redis 队列中仍有未 ACK 的任务。</li>
     * </ul>
     *
     * <p>恢复来源：</p>
     * <ol>
     *     <li>优先读取 Redis Set 中记录过的 title。</li>
     *     <li>再读取数据库中的厅组 title，作为兜底。</li>
     * </ol>
     */
    private void recoverAwardTitleQueue() {
        // 用 Set 去重，避免同一个 title 重复触发。
        Set<String> titles = new HashSet<>();

        try {
            // 从 Redis Set 中读取之前写入过队列的 title。
            Set<Object> redisTitles = redisUtils.sGet(AWARD_TITLE_QUEUE_TITLES_KEY);
            if (redisTitles != null) {
                for (Object title : redisTitles) {
                    if (title != null && !StringUtils.isEmpty(String.valueOf(title))) {
                        titles.add(String.valueOf(title));
                    }
                }
            }
        } catch (Exception e) {
            // Redis Set 读取失败时不影响服务启动，后面会尝试从数据库厅组列表恢复。
            log.warn("读取后台验派title队列集合失败，启动恢复继续使用厅组列表", e);
        }

        try {
            // 从数据库读取所有厅组 title，防止 Redis Set 丢失导致队列无法恢复。
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

        // 遍历所有 title，如果队列中还有任务，就重新触发消费。
        for (String title : titles) {
            long queueSize = redisUtils.lGetListSize(getAwardTitleQueueKey(title));
            if (queueSize > 0) {
                log.info("发现未完成后台验派title队列，准备恢复处理，title={}，queueSize={}", title, queueSize);
                triggerAwardTitleQueue(title);
            }
        }
    }

    /**
     * 触发指定 title 的队列消费。
     *
     * <p>核心逻辑：</p>
     * <ul>
     *     <li>如果 title 为空，直接返回。</li>
     *     <li>如果该 title 已经在消费中，直接返回，避免重复启动消费者。</li>
     *     <li>如果没有在消费，则提交一个单线程任务去执行 drainAwardTitleQueue。</li>
     * </ul>
     *
     * <p>为什么多个任务进来会等待：</p>
     * <pre>
     * if (!AWARD_TITLE_DRAINING.add(title)) {
     *     return;
     * }
     * </pre>
     *
     * <p>同一个 title 第一次 add 成功后，后续同 title 任务 add 会失败并 return。</p>
     * <p>这些后续任务只会进入 Redis 队列，等待当前消费者处理。</p>
     *
     * @param title 厅组 title
     */
    private void triggerAwardTitleQueue(String title) {
        // title 为空没有对应队列，直接忽略。
        if (StringUtils.isEmpty(title)) {
            return;
        }

        // JVM 内存级防重复消费控制。
        // add 成功：表示当前 title 没有消费者在跑，可以启动。
        // add 失败：表示当前 title 已经有消费者在跑，直接返回。
        if (!AWARD_TITLE_DRAINING.add(title)) {
            return;
        }

        try {
            // 获取当前 title 对应的单线程执行器，并提交消费任务。
            getAwardTitleExecutor(title).submit(() -> {
                try {
                    // 真正消费 Redis 队列的方法。
                    drainAwardTitleQueue(title);
                } finally {
                    // 当前消费流程结束后，移除“正在消费”标记。
                    AWARD_TITLE_DRAINING.remove(title);

                    // 如果队列中还有任务，说明消费过程中又写入了新任务，或失败任务保留在队列。
                    // 此时再次触发消费。
                    if (redisUtils.lGetListSize(getAwardTitleQueueKey(title)) > 0) {
                        triggerAwardTitleQueue(title);
                    }
                }
            });
        } catch (RuntimeException e) {
            // 如果提交线程任务失败，需要移除正在消费标记，否则后续该 title 会一直无法触发。
            AWARD_TITLE_DRAINING.remove(title);
            throw e;
        }
    }

    /**
     * 消费指定 title 的 Redis 派奖队列。
     *
     * <p>当前消费方式：</p>
     * <ol>
     *     <li>只要队列长度大于 0，就循环处理。</li>
     *     <li>每次处理前先获取 Redis title 锁。</li>
     *     <li>读取队首任务，但不立即删除。</li>
     *     <li>任务执行成功后，再从队列中删除该任务，完成 ACK。</li>
     *     <li>如果任务执行失败，任务保留在队列中，等待下次重试。</li>
     * </ol>
     *
     * <p>重要风险：</p>
     * <ul>
     *     <li>当前是 lGetIndex(queueKey, 0) 取队首，成功后 lRemove。</li>
     *     <li>如果队首任务一直失败，它不会被删除，后面的任务会一直处理不到。</li>
     *     <li>这就是多个任务进来后“后面一直等”的主要原因。</li>
     * </ul>
     *
     * @param title 厅组 title
     */
    private void drainAwardTitleQueue(String title) {
        // 当前 title 对应的 Redis List 队列 key。
        String queueKey = getAwardTitleQueueKey(title);

        // 只要队列中还有任务，就不断消费。
        while (redisUtils.lGetListSize(queueKey) > 0) {
            // Redis 分布式锁 key。
            String titleLockKey = getAwardTitleLockKey(title);

            // 本次加锁 token，用于安全释放锁。
            String titleLockToken = UUID.randomUUID().toString();

            // 标记本次是否成功获取锁，finally 中决定是否释放。
            boolean titleLockAcquired = false;

            // 当前正在处理的任务 JSON。
            String payload = null;

            try {
                // 等待获取 Redis title 锁。
                // 如果锁被其他线程或其他服务占用，这里会一直等待。
                waitForAwardTitleLock(titleLockKey, titleLockToken, title);
                titleLockAcquired = true;

                // 读取队首任务。
                // 注意：这里不是弹出，所以失败时任务仍会留在队首。
                Object queuedValue = redisUtils.lGetIndex(queueKey, 0);
                if (queuedValue == null) {
                    return;
                }

                // Redis 中取出的值转成字符串 JSON。
                payload = String.valueOf(queuedValue);

                AwardTitleQueueTask task;
                try {
                    // 反序列化队列任务。
                    task = JSON.parseObject(payload, AwardTitleQueueTask.class);
                } catch (Exception parseException) {
                    // JSON 解析失败，说明这个任务数据坏了，直接移除，避免阻塞后续任务。
                    log.warn("后台验派title队列任务JSON解析失败，已移除，title={}，payload={}", title, payload, parseException);
                    redisUtils.lRemove(queueKey, 1, payload);
                    continue;
                }

                // 校验任务必要字段。
                if (task == null || StringUtils.isEmpty(task.getTitle()) || StringUtils.isEmpty(task.getIssue())
                        || ObjectUtils.isEmpty(task.getLotteryId())) {
                    // 无效任务直接移除，避免阻塞后续任务。
                    log.warn("后台验派title队列任务数据无效，已移除，title={}，payload={}", title, payload);
                    redisUtils.lRemove(queueKey, 1, payload);
                    continue;
                }

                // 执行真正的派奖处理。
                processAwardTitleTask(task);

                // 执行成功后删除该任务，表示 ACK 成功。
                long removed = redisUtils.lRemove(queueKey, 1, payload);
                if (removed <= 0) {
                    // 如果删除失败，任务可能下次被重复处理。
                    log.warn("后台验派title队列任务ACK失败，可能会被重复处理，taskId={}，title={}，彩种ID={}，奖期={}",
                            task.getTaskId(), task.getTitle(), task.getLotteryId(), task.getIssue());
                }
            } catch (Exception e) {
                // 发生异常时不删除任务，让任务保留在队列中等待重试。
                // 注意：如果这个任务每次都失败，它会一直停留在队首，导致后面的任务永远处理不到。
                log.error("后台验派title队列任务执行失败，保留队列等待重试，title={}，payload={}", title, payload, e);

                // 睡眠一段时间后退出当前 drain。
                // 外层 trigger 的 finally 会发现队列还有数据，然后再次触发消费。
                sleepBeforeAwardTitleQueueRetry();
                return;
            } finally {
                // 如果本次成功获取了 Redis 锁，则尝试释放。
                if (titleLockAcquired && !redisUtils.unlock(titleLockKey, titleLockToken)) {
                    // 释放失败可能是锁已经过期，或者 token 不匹配。
                    log.warn("后台验派title锁未释放或已过期，title={}", title);
                }
            }
        }
    }

    /**
     * 处理单个派奖队列任务。
     *
     * <p>流程：</p>
     * <ol>
     *     <li>根据 masterId 查询厅组。</li>
     *     <li>组装 IssueInfoEntity。</li>
     *     <li>根据 lotteryId 查询彩种。</li>
     *     <li>调用 executeLotteryDraw 执行具体彩种派奖逻辑。</li>
     * </ol>
     *
     * @param task 队列任务
     */
    private void processAwardTitleTask(AwardTitleQueueTask task) {
        // 根据任务中的 masterId 查询厅组信息。
        RoomMasterEntity roomMaster = loadRoomMaster(task);

        // 找不到厅组时跳过该任务。
        // 注意：这里没有抛异常，所以 drainAwardTitleQueue 会继续 ACK 删除这个任务。
        if (ObjectUtils.isEmpty(roomMaster)) {
            log.warn("后台验派title队列任务找不到厅组，已跳过，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    task.getTaskId(), task.getTitle(), task.getMasterId(), task.getLotteryId(), task.getIssue());
            return;
        }

        // 组装奖期对象，只填充后续派奖需要的字段。
        IssueInfoEntity issueInfo = new IssueInfoEntity();
        issueInfo.setLotteryId(task.getLotteryId());
        issueInfo.setIssue(task.getIssue());
        issueInfo.setCode(task.getCode());

        // 查询彩种配置。
        LotteryEntity lottery = lotteryMapper.selectById(task.getLotteryId());

        // 根据彩种 functionType 调用对应派奖方法。
        executeLotteryDraw(roomMaster, issueInfo, lottery, task.getTaskId());
    }

    /**
     * 根据任务中的 masterId 查询厅组。
     *
     * <p>当前只按 masterId 查询。</p>
     * <p>如果 masterId 为空或者查不到，则返回 null。</p>
     *
     * @param task 队列任务
     * @return 厅组实体，找不到返回 null
     */
    private RoomMasterEntity loadRoomMaster(AwardTitleQueueTask task) {
        // masterId 不为空时才查询。
        if (task.getMasterId() != null) {
            LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoomMasterEntity::getMasterId, task.getMasterId());
            RoomMasterEntity roomMaster = roomMasterMapper.selectOne(wrapper);
            if (!ObjectUtils.isEmpty(roomMaster)) {
                return roomMaster;
            }
        }

        // 查不到厅组。
        return null;
    }

    /**
     * 执行具体彩种的派奖逻辑。
     *
     * <p>根据 lottery.functionType 分发到不同的 awardGivingService 方法：</p>
     * <ul>
     *     <li>VN_S / VN_C：越南自开。</li>
     *     <li>VN_N：越南北部。</li>
     *     <li>TH / TH_30S：泰国彩。</li>
     *     <li>LA / MY：老挝彩 / 马来西亚彩。</li>
     *     <li>K3：快 3。</li>
     * </ul>
     *
     * <p>如果具体 notice 方法抛异常，会继续向外抛 RuntimeException。</p>
     * <p>外层 drainAwardTitleQueue 会捕获异常，并保留任务等待重试。</p>
     *
     * @param roomMaster 厅组信息
     * @param issueInfo 奖期信息
     * @param lottery 彩种信息
     * @param taskId 队列任务 ID
     */
    private void executeLotteryDraw(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo, LotteryEntity lottery, String taskId) {
        try {
            // 彩种不存在时跳过，不抛异常。
            if (ObjectUtils.isEmpty(lottery)) {
                log.warn("后台验奖派奖跳过，彩种不存在，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                        taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
                return;
            }

            // 开始派奖日志。
            log.info("后台验奖派奖开始，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());

                NoticeReq n = new NoticeReq();                          // 组装通知派奖请求对象。
                n.setRoomMaster(roomMaster);                            // 设置厅组对象。
                n.setTableName(roomMaster.getTitle() + "_issue_info");  // 设置厅组奖期表名，例如 cn0003_issue_info。
                n.setTitle(roomMaster.getTitle());                      // 设置 title。
                n.setIssue(issueInfo.getIssue());                       // 设置奖期。
                n.setCode(issueInfo.getCode());                         // 设置开奖号码。
                n.setLotteryId(issueInfo.getLotteryId());               // 设置彩种 ID。

            // 根据彩种 functionType 调用对应的验奖派奖方法。
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
                    // 未配置的 functionType 不执行派奖，只打印日志。
                    log.warn("unsupported lottery function type, title={}，厅主ID={}，彩种ID={}，奖期={}，functionType={}",
                            roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(),
                            issueInfo.getIssue(), lottery.getFunctionType());
                    break;
            }
            // 派奖完成日志。
            log.info("后台验奖派奖完成，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
        } catch (Exception e) {
            // 具体派奖方法失败时抛 RuntimeException。
            // 外层 drainAwardTitleQueue 捕获后会保留任务等待重试。
            log.error("后台验奖派奖失败，taskId={}，title={}，厅主ID={}，彩种ID={}，奖期={}",
                    taskId, roomMaster.getTitle(), roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取指定 title 对应的单线程执行器。
     *
     * <p>computeIfAbsent 的作用：</p>
     * <ul>
     *     <li>如果该 title 已经有执行器，直接返回。</li>
     *     <li>如果没有，就创建一个新的单线程执行器。</li>
     * </ul>
     *
     * <p>线程名格式：</p>
     * <pre>
     * award-title-cn0003
     * </pre>
     *
     * <p>注意：该执行器没有主动 shutdown，服务运行期间会一直存在。</p>
     *
     * @param title 厅组 title
     * @return 单线程执行器
     */
    private ExecutorService getAwardTitleExecutor(String title) {
        return AWARD_TITLE_EXECUTORS.computeIfAbsent(title, key ->
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("award-title-" + key);
                    return thread;
                }));
    }

    /**
     * 等待并获取 title 的 Redis 分布式锁。
     *
     * <p>逻辑：</p>
     * <ol>
     *     <li>不断尝试 redisUtils.setLock。</li>
     *     <li>获取成功后返回。</li>
     *     <li>获取失败时，每 100ms 睡眠后继续重试。</li>
     * </ol>
     *
     * <p>注意：</p>
     * <ul>
     *     <li>这个方法没有最大等待时间。</li>
     *     <li>如果锁一直被占用，当前线程会一直等待。</li>
     *     <li>如果需要避免永久等待，可以加最大等待时间或失败退出逻辑。</li>
     * </ul>
     *
     * @param titleLockKey Redis 锁 key
     * @param titleLockToken 锁 token，用于释放锁时校验
     * @param title 厅组 title
     */
    private void waitForAwardTitleLock(String titleLockKey, String titleLockToken, String title) {
        // 记录开始等待时间，用于打印等待耗时。
        long startTime = System.currentTimeMillis();

        // 避免重复打印“锁被占用”日志。
        boolean waitLogged = false;

        while (true) {
            // 尝试获取 Redis 锁。
            if (redisUtils.setLock(titleLockKey, titleLockToken, AWARD_TITLE_LOCK_EXPIRE_SECONDS)) {
                long waitMs = System.currentTimeMillis() - startTime;

                // 如果之前等待过，获取成功后打印等待完成日志。
                if (waitLogged) {
                    log.info("后台验派title锁等待完成，title={}，waitMs={}", title, waitMs);
                }
                return;
            }

            // 第一次发现锁被占用时打印日志。
            if (!waitLogged) {
                waitLogged = true;
                log.info("后台验派title锁被占用，进入排队等待，title={}", title);
            }

            try {
                // 锁被占用，等待一小段时间后重试。
                Thread.sleep(AWARD_TITLE_LOCK_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                // 恢复线程中断状态。
                Thread.currentThread().interrupt();

                // 抛出异常，让外层处理。
                throw new IllegalStateException("Interrupted while waiting for award title lock", e);
            }
        }
    }

    /**
     * 队列任务失败后的睡眠方法。
     *
     * <p>用于避免任务失败后立刻无限快速重试，打爆日志或数据库。</p>
     */
    private void sleepBeforeAwardTitleQueueRetry() {
        try {
            Thread.sleep(AWARD_TITLE_QUEUE_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            // 恢复线程中断状态。
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 拼接 Redis title 锁 key。
     *
     * @param title 厅组 title
     * @return Redis 锁 key
     */
    private String getAwardTitleLockKey(String title) {
        return AWARD_TITLE_LOCK_PREFIX + title;
    }

    /**
     * 拼接 Redis title 队列 key。
     *
     * @param title 厅组 title
     * @return Redis List 队列 key
     */
    private String getAwardTitleQueueKey(String title) {
        return AWARD_TITLE_QUEUE_PREFIX + title;
    }

    /**
     * Redis 队列中保存的派奖任务对象。
     *
     * <p>任务会被 JSON 序列化后写入 Redis List。</p>
     * <p>消费时会反序列化回 AwardTitleQueueTask，再执行派奖逻辑。</p>
     */
    @Data
    public static class AwardTitleQueueTask {

        /**
         * 任务唯一 ID。
         *
         * <p>用于日志追踪。</p>
         */
        private String taskId;

        /**
         * 厅组 title。
         *
         * <p>用于区分不同 Redis 队列和不同锁。</p>
         */
        private String title;

        /**
         * 厅主 ID。
         *
         * <p>用于重新查询 RoomMasterEntity。</p>
         */
        private Integer masterId;

        /**
         * 彩种 ID。
         */
        private Long lotteryId;

        /**
         * 奖期期号。
         */
        private String issue;

        /**
         * 开奖号码。
         */
        private String code;

        /**
         * 任务创建时间戳。
         */
        private Long createdAt;
    }

    /**
     * 测试数据生成方法。
     *
     * <p>当前方法没有被主流程调用。</p>
     * <p>逻辑：新开线程，根据彩种 ID 创建测试数据；K3 彩种不创建。</p>
     *
     * @param issueInfo 奖期信息
     */
    private void FakeIssue(IssueInfoEntity issueInfo) {
        new Thread(() -> {
            LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
            if (!lottery.getFunctionType().equals("K3")) {
                awardService.createData(Integer.parseInt(issueInfo.getLotteryId().toString()));
            }
        }).start();
    }
}
