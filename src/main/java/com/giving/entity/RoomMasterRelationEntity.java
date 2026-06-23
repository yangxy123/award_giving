package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 厅主关系配置
 * @TableName room_master_relation
 */
@Data
@TableName(value = "room_master_relation")
public class RoomMasterRelationEntity implements Serializable {

    @TableId
    private Long id;

    private Integer masterId;

    private String category;

    private String value;

    private Date createdAt;

    private Date updatedAt;
}
