package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 玩法黑名单
 * @TableName blocked_method
 */
@Data
@TableName(value = "blocked_method")
public class BlockedMethodEntity implements Serializable {

    @TableId
    private Long blockedMethodId;

    private Integer roomMasterId;

    private String operator;

    private String blockedList;

    private String blockedPrizeSetKey;

    private Date createdAt;

    private Date updatedAt;
}
