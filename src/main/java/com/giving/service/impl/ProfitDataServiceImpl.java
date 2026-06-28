package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.enums.RedisKeyEnums;
import com.giving.service.ProfitDataService;
import com.giving.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ProfitDataServiceImpl implements ProfitDataService {
    private static final Set<Integer> PROFIT_LOTTERY_IDS = new HashSet<>(
            Arrays.asList(223, 212, 130, 243, 132, 281));
    private static final String PROFIT_REDIS_LOCK_KEY = "lock:c_profit_data";
    private static final long PROFIT_REDIS_LOCK_EXPIRE_SECONDS = 10L;
    private static final int PROFIT_REDIS_LOCK_RETRY_TIMES = 10;
    private static final long PROFIT_REDIS_LOCK_RETRY_INTERVAL_MS = 50L;

    @Autowired
    private RedisUtils redisUtils;
    @Value("${profit.room-master-ids:}")
    private String profitRoomMasterIds;

    @Override
    public void addPriceAfterCommit(RoomMasterEntity roomMaster, List<BetInfoEntity> projects) {
        BigDecimal amount = sumProfitAmount(roomMaster, projects, true);
        registerProfitDataAfterCommit("_price", amount);
    }

    @Override
    public void addBonusAfterCommit(RoomMasterEntity roomMaster, List<BetInfoEntity> projects) {
        BigDecimal amount = sumProfitAmount(roomMaster, projects, false);
        registerProfitDataAfterCommit("_bonus", amount);
    }

    private BigDecimal sumProfitAmount(RoomMasterEntity roomMaster, List<BetInfoEntity> projects, boolean price) {
        if (projects == null || projects.isEmpty()) {
            return null;
        }
        BigDecimal total = null;
        for (BetInfoEntity project : projects) {
            if (!isProfitProject(roomMaster, project)) {
                continue;
            }
            BigDecimal amount = price
                    ? getTotalPrice(project).subtract(getUserPoint(project))
                    : getBonus(project);
            total = total == null ? amount : total.add(amount);
        }
        return total;
    }

    private boolean isProfitProject(RoomMasterEntity roomMaster, BetInfoEntity project) {
        return roomMaster != null
                && roomMaster.getMasterId() != null
                && project != null
                && project.getLotteryId() != null
                && PROFIT_LOTTERY_IDS.contains(project.getLotteryId())
                && isProfitRoomMaster(roomMaster.getMasterId());
    }

    private boolean isProfitRoomMaster(Integer masterId) {
        if (!StringUtils.hasText(profitRoomMasterIds)) {
            return false;
        }
        String currentMasterId = String.valueOf(masterId);
        for (String configuredMasterId : profitRoomMasterIds.split(",")) {
            if (currentMasterId.equals(configuredMasterId.trim())) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal getTotalPrice(BetInfoEntity project) {
        return project.getTotalPrice() == null ? BigDecimal.ZERO : BigDecimal.valueOf(project.getTotalPrice());
    }

    private BigDecimal getUserPoint(BetInfoEntity project) {
        if (!StringUtils.hasText(project.getUserPoint())) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(project.getUserPoint().trim());
    }

    private BigDecimal getBonus(BetInfoEntity project) {
        return project.getBonus() == null ? BigDecimal.ZERO : BigDecimal.valueOf(project.getBonus());
    }

    private void registerProfitDataAfterCommit(String suffix, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        Runnable updateProfitData = () -> addProfitData(suffix, amount);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    updateProfitData.run();
                }
            });
        } else {
            updateProfitData.run();
        }
    }

    private void addProfitData(String suffix, BigDecimal amount) {
        String lockToken = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            for (int attempt = 1; attempt <= PROFIT_REDIS_LOCK_RETRY_TIMES; attempt++) {
                if (redisUtils.setLock(PROFIT_REDIS_LOCK_KEY, lockToken, PROFIT_REDIS_LOCK_EXPIRE_SECONDS)) {
                    locked = true;
                    break;
                }
                if (attempt < PROFIT_REDIS_LOCK_RETRY_TIMES) {
                    Thread.sleep(PROFIT_REDIS_LOCK_RETRY_INTERVAL_MS);
                }
            }
            if (!locked) {
                log.error("盈利率统计Redis锁获取失败，fieldSuffix={}，amount={}", suffix, amount);
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String field = sdf.format(new Date()) + suffix;
            String currentValue = redisUtils.rawHget(RedisKeyEnums.C_PROFIT_DATA.key, field);
            BigDecimal newValue = StringUtils.hasText(currentValue)
                    ? new BigDecimal(currentValue).add(amount).setScale(6, RoundingMode.DOWN)
                    : amount.setScale(6, RoundingMode.DOWN);
            redisUtils.rawHset(RedisKeyEnums.C_PROFIT_DATA.key, field, newValue.toPlainString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("盈利率统计Redis锁等待被中断，fieldSuffix={}，amount={}", suffix, amount, e);
        } catch (Exception e) {
            log.error("盈利率统计Redis写入失败，fieldSuffix={}，amount={}", suffix, amount, e);
        } finally {
            if (locked && !redisUtils.unlock(PROFIT_REDIS_LOCK_KEY, lockToken)) {
                log.warn("盈利率统计Redis锁未释放或已过期，lockKey={}", PROFIT_REDIS_LOCK_KEY);
            }
        }
    }
}
