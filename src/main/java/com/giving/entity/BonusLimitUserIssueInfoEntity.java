package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户单期奖金限额累计
 * @TableName temp_bonus_limit_user_issue_info
 */
@Data
@TableName(value = "temp_bonus_limit_user_issue_info")
public class BonusLimitUserIssueInfoEntity {

    private String userId;

    private Integer lotteryId;

    private Integer methodId;

    private String codeName;

    private String issue;

    private BigDecimal priceUser;
}
