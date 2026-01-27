package com.giving.mapper;

import com.giving.entity.UserFundEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.req.NoticeReq;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
* @author zzby
* @description 针对表【TEMP_user_fund(用户钱包)】的数据库操作Mapper
* @createDate 2026-01-09 16:57:33
* @Entity com.giving.entity.TempUserFund
*/
public interface UserFundMapper extends BaseMapper<UserFundEntity> {

    /**
     * 修改已经锁定的钱包金额
     * @param updateFund
     * @param title
     * @return
     */
    int updateAddOrdersList(@Param("updateFund") UserFundEntity updateFund, @Param("title") String title);

    /**
     *通过type 与锁定状态取得一条
     * @param title
     * @param userFund
     * @return
     */
    UserFundEntity selectByUserAndTypeOne(@Param("title") String title, @Param("userFund")UserFundEntity userFund);

    /**
     * 执行锁定用户钱包
     * @param title
     * @param userFund
     * @return
     */
    int updateLockedById(@Param("title") String title, @Param("userFund") UserFundEntity userFund);

    /**
     * 取得用户全部钱包合集
     * @param title
     * @param userId
     * @return
     */
    UserFundEntity selectByUserSum(@Param("title") String title, @Param("userId")String userId);

    /**
     * 批量解锁--1
     * @param title
     * @param userFundMap
     * @param walletType
     * @param lockAction
     */
    void doLockUserFund(@Param("title") String title,@Param("userFundMap") Map<String,UserFundEntity> userFundMap,
                        @Param("walletType") Integer walletType,@Param("lockAction") String lockAction);

    /**
     * 批量修改钱包
     * @param title
     * @param userFundMap
     */
    int doUpdateAddOrdersList(@Param("title") String title,@Param("userFundMap") Map<String,UserFundEntity> userFundMap);


}





