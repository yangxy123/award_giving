package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 帐变纪录
 * @TableName TEMP_orders
 */
@TableName(value ="TEMP_orders")
@Data
public class OrdersEntity implements Serializable {
    /**
     * 编号
     */
    @TableId
    private String entry;

    /**
     * 彩种ID
     */
    private Integer lotteryId;

    /**
     * 玩法ID
     */
    private Integer methodId;

    /**
     * 追号任务Id
     */
    private String taskId;

    /**
     * 方案ID
     */
    private String projectId;

    /**
     * 帐变发生人ID
     */
    private String fromuserId;

    /**
     * 帐变接收人ID
     */
    private String touserId;

    /**
     * 总代管理员ID
     */
    private String agentId;

    /**
     * 管理员ID
     */
    private Integer adminId;

    /**
     * 帐变类型
     */
    private Integer orderTypeId;

    /**
     * 
     */
    private String adminName;

    /**
     * 奖期期号
     */
    private String issue;

    /**
     * 帐变标题
     */
    private String title;

    /**
     * 本条账变所产生的资金变化量
     */
    private BigDecimal amount;

    /**
     * 帐变描述
     */
    private String description;

    /**
     * 帐变前频道资金
     */
    private BigDecimal preBalance;

    /**
     * 帐变前冻结资金
     */
    private BigDecimal preHold;

    /**
     * 帐变前可用资金
     */
    private BigDecimal preAvailable;

    /**
     * 帐变后可用资金
     */
    private BigDecimal channelBalance;

    /**
     * 帐变后的冻结资金
     */
    private BigDecimal holdBalance;

    /**
     * 帐变后可用资金
     */
    private BigDecimal availableBalance;

    /**
     * 用户真实IP
     */
    private String clientIp;

    /**
     * CDNIP
     */
    private String proxyIp;

    /**
     * MYSQL帐变时间 ( MYSQL NOW() )
     */
    private Date times;

    /**
     * 动作时间 ( PHP 中定义的时间)
     */
    private Date actionTime;

    /**
     * 转账2的用户ID
     */
    private String transferUserId;

    /**
     * 转账平台2
     */
    private Integer transferChannelId;

    /**
     * 转账2的ID
     */
    private String transferOrderId;

    /**
     * 帐变状态,1:请求;2:成功;3:失败
     */
    private Integer transferStatus;

    /**
     * 账变唯一KEY (目前主要用于频道转账)
     */
    private String uniqueKey;

    /**
     * 模式id
     */
    private String modes;

    /**
     * 
     */
    private String cardnotice;

    /**
     * 1:未發送，2:進行中，3:已完成
     */
    private Integer sendMoneyToPlatform;

    /**
     * 
     */
    private String platform;

    /**
     * 
     */
    private Date createdAt;

    /**
     * 
     */
    private Date updatedAt;

    /**
     * 
     */
    private String thirdPartyTrxId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}