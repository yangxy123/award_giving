package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.RoomMasterRelationEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 厅主关系配置Mapper
 */
public interface RoomMasterRelationMapper extends BaseMapper<RoomMasterRelationEntity> {

    /**
     * 根据厅主和分类查询配置
     * @param masterId 厅主ID
     * @param category 分类
     * @return 配置
     */
    RoomMasterRelationEntity selectByMasterIdAndCategory(@Param("masterId") Integer masterId,
                                                         @Param("category") String category);
}
