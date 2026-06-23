package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.BlockedMethodEntity;
import org.apache.ibatis.annotations.Param;

/**
 * 玩法黑名单Mapper
 */
public interface BlockedMethodMapper extends BaseMapper<BlockedMethodEntity> {

    /**
     * 根据厅主和operator查询玩法黑名单
     * @param roomMasterId 厅主ID
     * @param operator operator代码
     * @return 玩法黑名单
     */
    BlockedMethodEntity selectByRoomMasterIdAndOperator(@Param("roomMasterId") Integer roomMasterId,
                                                        @Param("operator") String operator);
}
