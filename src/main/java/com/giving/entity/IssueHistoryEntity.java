package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 历史奖期
 * @TableName issue_history
 */
@TableName(value ="issue_history")
@Data
public class IssueHistoryEntity implements Serializable {
    /**
     * 彩种ID
     */
    private Long lotteryId;

    /**
     * 奖期期号
     */
    private String issue;

    /**
     * 开奖号码
     */
    private String code;

    /**
     * 属于哪天的奖期
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
     * 开奖奖期状态 0:未写入;1:写入待验证;2:已验证;3:官方未开奖
     */
    private Integer statusCode;

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