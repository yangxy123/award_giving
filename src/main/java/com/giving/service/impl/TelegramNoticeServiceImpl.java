package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.service.TelegramNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class TelegramNoticeServiceImpl implements TelegramNoticeService {

    private static final AtomicInteger WARNING_THREAD_SEQ = new AtomicInteger(0);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${telegram.award-warning.enabled:true}")
    private boolean awardWarningEnabled;

    @Value("${telegram.award-warning.bot-token:}")
    private String awardWarningBotToken;

    @Value("${telegram.award-warning.chat-id:}")
    private String awardWarningChatId;

    @Value("${telegram.award-warning.api-url:https://api.telegram.org}")
    private String telegramApiUrl;

    @Value("${profit.WarningFund:}")
    private String largeBonusWarningFund;

    @Value("${profit.amount:}")
    private String profitAmount;

    @Override
    public void sendLargeBonusAwardWarningAfterCommit(RoomMasterEntity roomMaster, List<BetInfoEntity> projects) {
        BigDecimal warningFund = parseWarningFund();
        if (warningFund == null || projects == null || projects.isEmpty()) {
            return;
        }

        List<BetInfoEntity> warningProjects = new ArrayList<>();
        for (BetInfoEntity project : projects) {
            if (isLargeBonusProject(project, warningFund)) {
                warningProjects.add(snapshotProject(project));
            }
        }
        if (warningProjects.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendLargeBonusWarningsAsync(roomMaster, warningProjects, warningFund);
                }
            });
            return;
        }

        sendLargeBonusWarningsAsync(roomMaster, warningProjects, warningFund);
    }

    private BigDecimal parseWarningFund() {
        String warningFundValue = StringUtils.hasText(largeBonusWarningFund)
                ? largeBonusWarningFund
                : profitAmount;
        if (!StringUtils.hasText(warningFundValue)) {
            return null;
        }
        try {
            return new BigDecimal(warningFundValue.trim());
        } catch (NumberFormatException e) {
            log.warn("大额奖金派奖预警阈值配置错误，profit.WarningFund={}，profit.amount={}",
                    largeBonusWarningFund, profitAmount);
            return null;
        }
    }

    private boolean isLargeBonusProject(BetInfoEntity project, BigDecimal warningFund) {
        if (project == null || project.getBonus() == null) {
            return false;
        }
        return BigDecimal.valueOf(project.getBonus()).compareTo(warningFund) >= 0;
    }

    private BetInfoEntity snapshotProject(BetInfoEntity project) {
        BetInfoEntity snapshot = new BetInfoEntity();
        snapshot.setProjectId(project.getProjectId());
        snapshot.setUserId(project.getUserId());
        snapshot.setBonus(project.getBonus());
        snapshot.setLotteryId(project.getLotteryId());
        snapshot.setIssue(project.getIssue());
        return snapshot;
    }

    private void sendLargeBonusWarningsAsync(RoomMasterEntity roomMaster, List<BetInfoEntity> projects, BigDecimal warningFund) {
        Thread thread = new Thread(() -> {
            for (BetInfoEntity project : projects) {
                try {
                    sendLargeBonusWarning(roomMaster, project, warningFund);
                } catch (Exception e) {
                    log.error("发送大额奖金派奖Telegram预警失败，projectId={}，userId={}",
                            project.getProjectId(), project.getUserId(), e);
                }
            }
        }, "telegram-large-bonus-warning-" + WARNING_THREAD_SEQ.incrementAndGet());
        thread.start();
    }

    private void sendLargeBonusWarning(RoomMasterEntity roomMaster, BetInfoEntity project, BigDecimal warningFund) {
        if (!awardWarningEnabled) {
            return;
        }
        if (!StringUtils.hasText(awardWarningBotToken) || !StringUtils.hasText(awardWarningChatId)) {
            log.warn("大额奖金派奖Telegram预警未发送，telegram.award-warning.bot-token或chat-id未配置");
            return;
        }

        String message = buildLargeBonusWarningMessage(roomMaster, project, warningFund);
        for (String chatId : awardWarningChatId.split(",")) {
            if (!StringUtils.hasText(chatId)) {
                continue;
            }
            doSendMessage(chatId.trim(), message);
        }
    }

    private void doSendMessage(String chatId, String message) {
        String url = trimTrailingSlash(telegramApiUrl) + "/bot" + normalizeBotToken(awardWarningBotToken) + "/sendMessage";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("text", message);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn("大额奖金派奖Telegram预警发送失败，chatId={}，status={}，body={}",
                    chatId, response.getStatusCodeValue(), response.getBody());
        }
    }

    private String normalizeBotToken(String botToken) {
        String token = botToken.trim();
        if (token.regionMatches(true, 0, "bot", 0, 3)) {
            return token.substring(3);
        }
        return token;
    }

    private String buildLargeBonusWarningMessage(RoomMasterEntity roomMaster, BetInfoEntity project, BigDecimal warningFund) {
        BigDecimal bonus = BigDecimal.valueOf(project.getBonus());
        return "大额奖金派发预警"
                + "\n厅组ID：" + roomMaster.getMasterId()
                + "\n厅组名称：" + roomMaster.getName()
                + "\n商户：" + resolveOperator(roomMaster)
                + "\n用户ID：" + valueOrDash(project.getUserId())
                + "\n订单ID：" + valueOrDash(project.getProjectId())
                + "\n派奖金额：" + formatAmount(bonus)
                + "\n触发阈值：" + formatAmount(warningFund)
                + "\n彩种ID：" + valueOrDash(project.getLotteryId())
                + "\nissue：" + valueOrDash(project.getIssue());
    }

    private String resolveOperator(RoomMasterEntity roomMaster) {
        if (roomMaster == null) {
            return "-";
        }
        if (StringUtils.hasText(roomMaster.getName())) {
            return roomMaster.getName().trim();
        }
        if (StringUtils.hasText(roomMaster.getNickName())) {
            return roomMaster.getNickName().trim();
        }
        if (StringUtils.hasText(roomMaster.getTitle())) {
            return roomMaster.getTitle().trim();
        }
        return valueOrDash(roomMaster.getMasterId());
    }

    private String valueOrDash(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "https://api.telegram.org";
        }
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
