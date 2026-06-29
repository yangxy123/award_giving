package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @TableName currency_stake
 */
@Data
@TableName(value = "currency_stake")
public class CurrencyStakeEntity implements Serializable {

    private String currency;

    private String functionType;

    private BigDecimal stake;

    private BigDecimal toCny;

    private String base;

    private Integer pow;

    private BigDecimal oneToCny;

    private static final long serialVersionUID = 1L;
}
