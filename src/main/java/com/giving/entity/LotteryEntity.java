package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * @TableName lottery
 */
@TableName(value ="lottery")
@Data
public class LotteryEntity implements Serializable {
    @TableId
    private Long lotteryId;

    private String lotteryGroup;

    private String methodGroup;

    private String cnname;

    private String enname;

    private Integer sorts;

    private Integer lotteryType;

    private String functionType;

    private String issueset;

    private Integer weekcycle;

    private Double minCommissionGap;

    private Double minProfit;

    private String issueRule;

    private String description;

    private String numberRule;

    private Integer unlocked;

    private String country;

    private String continent;

    private Integer isActive;

    private Date closedAt;

    private Date createdAt;

    private Date updatedAt;

    private Integer isLocal;

    private String initIssue;

    private String isAlert;

    private String alertPrize;

    private Double drawPercent;

    private Integer seriesId;

    private Integer frontDrawTime;

    private Integer delayCheckTime;

    private Integer shouldDraw;

    private String saleTime;

    private Integer canSet;

    private Integer drawOpen;

    private Integer betCost;

    private Integer isAutoodds;

    private Integer pendingTime;

    private Long aliasId;

    private Integer menuId;

    private static final long serialVersionUID = 1L;
}