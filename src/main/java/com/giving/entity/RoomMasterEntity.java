package com.giving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 厅主列表
 * @TableName room_master
 */
@TableName(value ="room_master")
@Data
public class RoomMasterEntity implements Serializable {
    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 厅主ID
     */
    private Integer masterId;

    /**
     * 商戶型態 (0 : B2B , 1 : GPI)
     */
    private Integer businessType;

    /**
     * 0: 正式廳，1: 測試廳
     */
    private Integer thirdpartyStatus;

    /**
     * 資料庫用title
     */
    private String title;

    /**
     * 厅主登陆帐号
     */
    private String name;

    /**
     * app_key
     */
    private String security;

    /**
     * 厅主昵称
     */
    private String nickName;

    /**
     * 厅主国家 ISO code
     */
    private String country;

    /**
     * 厅主语系 ISO code
     */
    private String userLang;

    /**
     * 厅主币种 ISO code
     */
    private String currency;

    /**
     * 厅主登入形态 (0 : B2B , 1 : GPI)
     */
    private Integer loginType;

    /**
     * 厅主投注形态 (0 : B2B , 1 : GPI)
     */
    private Integer betType;

    /**
     * 厅主派奖形态 (0 : B2B , 1 : GPI)
     */
    private Integer sendMoneyType;

    /**
     * 厅主钱包形态 (0 : B2B , 1 : GPI)
     */
    private Integer userWalletType;

    /**
     * 
     */
    private String thirdpartyAccesstokenCheckUrl;

    /**
     * 
     */
    private String thirdpartyLoginFailedUrl;

    /**
     * 
     */
    private String lotteryInService;

    /**
     * 
     */
    private String miscInfo;

    /**
     * 
     */
    private String cdnInfo;

    /**
     * 
     */
    private String directInfo;

    /**
     * 是否关闭: 1为开启 , 0 为关闭
     */
    private Integer isActive;

    /**
     * 返点bar是否关闭: 1为开启 , 0 为关闭
     */
    private Integer isShowPointBar;

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