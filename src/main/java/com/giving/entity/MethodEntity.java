package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 玩法表
 * @TableName method
 */
@Data
@TableName(value = "method")
public class MethodEntity implements Serializable {

    @TableId
    private Integer methodId;

    private Integer pId;

    private Integer lotteryId;

    private Integer crowdId;

    private String methodName;

    private String code;

    private String jscode;

    private Integer isSpecial;

    private String functionName;

    private String functionRule;

    private String description;

    private Integer isClose;

    private Integer isLock;

    private BigDecimal totalMoney;

    private String modes;

    private String frontName;

    private Date createdAt;

    private Date updatedAt;
}
