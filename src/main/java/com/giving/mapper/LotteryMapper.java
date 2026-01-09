package com.giving.mapper;

import com.giving.entity.LotteryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author zzby
* @description 针对表【lottery(彩票种类)】的数据库操作Mapper
* @createDate 2026-01-09 15:57:13
* @Entity com.giving.entity.Lottery
*/
public interface LotteryMapper extends BaseMapper<LotteryEntity> {

    List<LotteryEntity> selectLotterIdIn18();
}




