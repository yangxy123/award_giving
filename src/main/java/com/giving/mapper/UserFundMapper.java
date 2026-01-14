package com.giving.mapper;

import com.giving.entity.BetInfoEntity;
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

    UserFundEntity selectByNotice(@Param("noticeReq") NoticeReq noticeReq,@Param("userId") String userId);

    void updateUserFund(@Param("noticeReq") NoticeReq noticeReq,@Param("bonusMap") Map<String, BigDecimal> bonusMap);
}




