package com.giving.service.autoissue;

import lombok.Data;

import java.time.LocalTime;

/**
 * 官方越南彩固定开奖时间配置。
 */
@Data
public class VietnamTimeSetting {
    private final LocalTime saleEnd;
    private final LocalTime openDate;
    private final long startEndTimeDiffSeconds;
}
