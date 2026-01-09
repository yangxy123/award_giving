package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 用户钱包
 * @TableName TEMP_user_fund
 */
@TableName(value ="TEMP_user_fund")
@Data
public class UserFundEntity implements Serializable {
    /**
     * 编号
     */
    @TableId
    private String entry;

    /**
     * 用户ID
     */
    private String userid;

    /**
     * 钱包类别 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款
     */
    private Integer walletType;

    /**
     * 频道ID
     */
    private Integer channelid;

    /**
     * 帐户余额(可用+冻结) C
     */
    private BigDecimal channelbalance;

    /**
     *  可用余额 (E)
     */
    private BigDecimal availablebalance;

    /**
     *  冻结金额 (D)
     */
    private BigDecimal holdbalance;

    /**
     * 资金被锁状态, 0=正常, 1=被锁
     */
    private Integer islocked;

    /**
     * 資金鎖action
     */
    private String lockAction;

    /**
     * 最后更新时间
     */
    private Date lastupdatetime;

    /**
     *  用户最后活跃时间
     */
    private Date lastactivetime;

    /**
     * 
     */
    private Date createdAt;

    /**
     * 
     */
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}