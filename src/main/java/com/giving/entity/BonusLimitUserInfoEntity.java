package com.giving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户奖金限额配置
 * @TableName temp_bonus_limit_user_info
 */
@Data
@TableName(value = "temp_bonus_limit_user_info")
public class BonusLimitUserInfoEntity {

    @TableId(type = IdType.AUTO)
    private Integer bonusUserId;

    private String userId;

    private Integer lotteryId;

    private Integer methodId;

    private String codeName;

    private BigDecimal priceLimitUser;

    private Date updatedAt;
}
