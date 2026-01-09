package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 奖期信息
 * @TableName issue_info
 */
@Data
@TableName(value ="issue_info")
public class IssueInfoEntity implements Serializable {
    /**
     * 奖期ID
     */
    @TableId
    private Integer issueId;

    /**
     * 彩种ID
     */
    private Long lotteryId;

    /**
     * 开奖号码
     */
    private String code;

    /**
     * 奖期期号
     */
    private String issue;

    /**
     * 队列任务ID
     */
    private String jobId;

    /**
     * 开奖时间
     */
    private Date openDate;

    /**
     * 属于哪天的奖期;
     */
    private String belongDate;

    /**
     * 属于哪天的奖期, 自动产生奖期用
     */
    private Date autoissueBelongDate;

    /**
     * 本期销售开始时间
     */
    private Date saleStart;

    /**
     * 本期销售截至时间
     */
    private Date saleEnd;

    /**
     * 本期停止撤单时间
     */
    private Date cancelDeadline;

    /**
     * 实际录号时间
     */
    private Date writeTime;

    /**
     * 写入号码的管理员ID
     */
    private Integer writeId;

    /**
     * 0未抓号;1进行中;2已完成
     */
    private Integer statusFetch;

    /**
     * 开奖奖期状态 0:未写入;1:写入待验证;2:已验证;3:官方未开奖
     */
    private Integer statusCode;

    /**
     * 系统撤单状态 0.系统未撤单 1.提早开号撤单, 2.开奖号码错误撤单, 3.官方未开奖撤单
     */
    private Integer cancelStatus;

    /**
     * 
     */
    private Date createdAt;

    /**
     * 
     */
    private Date updatedAt;

    /**
     * 出号来源 0:待开号, 1:新初号机, 2:旧出号机, 3:自主生成, 4:官彩开号, 5.手动开号
     */
    private Integer drawType;

    /**
     * 乱数出号 0.否 1.是
     */
    private Integer random;

    /**
     * 是否为休市中 0:销售中, 1:休市中
     */
    private Integer isClose;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}