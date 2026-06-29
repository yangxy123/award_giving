package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.AutooddsEntity;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public interface AutooddsMapper extends BaseMapper<AutooddsEntity> {

    List<AutooddsEntity> selectByMethodAndCode(@Param("lotteryId") Integer lotteryId,
                                               @Param("methodId") Integer methodId,
                                               @Param("codeName") String codeName,
                                               @Param("likeCode") boolean likeCode);

    BigDecimal selectIssueTotalPrice(@Param("title") String title,
                                     @Param("aoId") Long aoId,
                                     @Param("issue") String issue,
                                     @Param("operator") String operator);

    Integer selectLatestCodeCount(@Param("aoId") Long aoId,
                                  @Param("saleStart") Date saleStart);
}
