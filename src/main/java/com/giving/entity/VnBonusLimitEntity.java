package com.giving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 泰国/越南号码累计奖金限额
 * @TableName temp_vn_bonus_limit
 */
@Data
@TableName(value = "temp_vn_bonus_limit")
public class VnBonusLimitEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String opCode;

    private Integer lotteryId;

    private String issue;

    private Integer methodId;

    private String codeNumber;

    private BigDecimal totalBonus;

    private BigDecimal totalAmount;

    private Integer totalCount;

    private Date createdAt;

    private Date updatedAt;
}
