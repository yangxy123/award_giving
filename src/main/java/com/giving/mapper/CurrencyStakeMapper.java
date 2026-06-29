package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.CurrencyStakeEntity;
import org.apache.ibatis.annotations.Param;

public interface CurrencyStakeMapper extends BaseMapper<CurrencyStakeEntity> {

    CurrencyStakeEntity selectByFunctionTypeAndCurrency(@Param("functionType") String functionType,
                                                        @Param("currency") String currency);
}
