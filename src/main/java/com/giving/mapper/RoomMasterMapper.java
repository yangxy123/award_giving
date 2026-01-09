package com.giving.mapper;

import com.giving.entity.RoomMasterEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author zzby
* @description 针对表【room_master(厅主列表)】的数据库操作Mapper
* @createDate 2026-01-04 14:18:43
* @Entity com.giving.entity.RoomMaster
*/
public interface RoomMasterMapper extends BaseMapper<RoomMasterEntity> {

    //查找title
    List<String> selectTitle();
}




