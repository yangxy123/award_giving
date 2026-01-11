package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName TEMP_projects_tmp
 */
@TableName(value ="TEMP_projects_tmp")
@Data
public class ProjectsTmpEntity implements Serializable {
    /**
     * 
     */
    @TableId
    private String tmpId;

    /**
     * 
     */
    private Integer issueId;

    /**
     * 
     */
    private Integer lotteryId;

    /**
     * 
     */
    private String projectId;

    /**
     * 
     */
    private Integer status;

    /**
     * 投注内容
     */
    private String tmpValue;

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