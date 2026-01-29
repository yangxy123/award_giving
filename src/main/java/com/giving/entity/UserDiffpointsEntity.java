package com.giving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 用户返点表
 * @TableName TEMP_user_diffpoints
 */
@TableName(value ="TEMP_user_diffpoints")
@Data
public class UserDiffpointsEntity implements Serializable {
    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Integer entry;

    /**
     * 彩种ID
     */
    private Integer lotteryId;

    /**
     * 奖期期号
     */
    private String issue;

    /**
     * 
     */
    private String userId;

    /**
     * 
     */
    private String projectId;

    /**
     * 
     */
    private BigDecimal diffmoney;

    /**
     * 用户的返点
     */
    private BigDecimal diffpoint;

    /**
     * 返点状态(0:未返;1:已返;2:已撤)
     */
    private Integer status;

    /**
     * 撤单状态(0:未撤;1:用户撤单;2:中奖后撤单;3:公司撤单)
     */
    private Integer cancelStatus;

    /**
     * 返点时间
     */
    private Date sendtime;

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