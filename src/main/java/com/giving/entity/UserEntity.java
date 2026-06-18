package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户表
 * @TableName TEMP_users
 */
@Data
@TableName(value = "TEMP_users")
public class UserEntity implements Serializable {

    @TableId
    private String userId;

    private String lvtopId;

    private String parenttree;

    private String operator;

    private String name;

    private String thirdpartyId;

    private String appAccount;

    private String nickName;

    private String isFrozen;

    private String frozenType;

    private String currency;

    private String isTester;

    private Integer isBlockhistory;

    private String isDeleted;

    private BigDecimal keepPoint;

    private Date createdAt;

    private Date updatedAt;
}
