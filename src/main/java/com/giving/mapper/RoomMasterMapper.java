package com.giving.mapper;

import com.giving.entity.RoomMasterEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.req.NoticeReq;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author zzby
* @description 针对表【room_master(厅主列表)】的数据库操作Mapper
* @createDate 2026-01-04 14:18:43
* @Entity com.giving.entity.RoomMaster
*/
public interface RoomMasterMapper extends BaseMapper<RoomMasterEntity> {

    //查找title
    @Select({"select * from room_master where is_active = 1"})
    List<RoomMasterEntity> selectTitle();

//    @Select({"insert into ${noticeReq.tableName} " +
//            "" })
    void createSpeculation(@Param("roomMaster") RoomMasterEntity roomMaster);
}




