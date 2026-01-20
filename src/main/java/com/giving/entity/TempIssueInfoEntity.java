package com.giving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 奖期信息
 * @TableName TEMP_issue_info
 */
@TableName(value ="TEMP_issue_info")
@Data
public class TempIssueInfoEntity implements Serializable {
    /**
     * 奖期ID
     */
    @TableId(type = IdType.AUTO)
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
     * 队列任务名称
     */
    private String jobName;

    /**
     * 队列任务ID
     */
    private String jobId;

    /**
     * 執行抓号的时间;
     */
    private Date drawDate;

    /**
     * 开奖时间
     */
    private Date openDate;

    /**
     * 属于哪天的奖期;
     */
    private String belongDate;

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
     * 最早录号时间
     */
    private Date earliestWriteTime;

    /**
     * 实际录号时间
     */
    private Date writeTime;

    /**
     * 写入号码的管理员ID
     */
    private Integer writeId;

    /**
     * 开奖号码验证时间
     */
    private Date verifyTime;

    /**
     * 验证管理员ID
     */
    private Integer verifyId;

    /**
     * 0未抓号;1进行中;2已完成
     */
    private Integer statusFetch;

    /**
     * 开奖奖期状态 0:未写入;1:写入待验证;2:已验证;3:官方未开奖
     */
    private Integer statusCode;

    /**
     * 扣款状态;0:未完成;1:进行中;2:已经完成
     */
    private Integer statusDeduct;

    /**
     * 返点状态;0:未开始;1:进行中;2:已完成
     */
    private Integer statusUserPoint;

    /**
     * 检查中奖状态;0:未开始;1:进行中;2:已经完成
     */
    private Integer statusCheckBonus;

    /**
     * 返奖状态;0:未开始;1:进行中;2:已经完成
     */
    private Integer statusBonus;

    /**
     * 追号单转注单状态;0:未开始;1:进行中;2:已经完成
     */
    private Integer statusTaskToProject;

    /**
     * 同步issuehistory;0=未同步;1=已同步
     */
    private Integer statusSynced;

    /**
     * 锁封表是否生成状态
     */
    private Integer statusLocks;

    /**
     * 效验字串
     */
    private String specialCode;

    /**
     * 效验状态;0: 未效验 ;1: 效验成功 ;2: 效验失败 ;3: 错误警报;4: 问题已处理
     */
    private Integer specialStatus;

    /**
     * 备份状态;0: 未备份; 1: 备份完成; 2:备份中
     */
    private Integer backupStatus;

    /**
     * 备份删除状态;0:未删除;1已删除
     */
    private Integer backupdelStatus;

    /**
     * 返点时间
     */
    private Date successTime1;

    /**
     * 派奖时间
     */
    private Date successTime2;

    /**
     * 0:无 ;
     */
    private Integer error;

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