package com.giving.mapper;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.UserDiffpointsEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author zzby
* @description 针对表【TEMP_user_diffpoints(用户返点表)】的数据库操作Mapper
* @createDate 2026-01-29 15:56:49
* @Entity com.giving.entity.UserDiffpoints
*/
public interface UserDiffpointsMapper extends BaseMapper<UserDiffpointsEntity> {

    int updateThreshold(@Param("title") String title,@Param("projects") List<BetInfoEntity> projects);

    /**
     * 查询注单返点记录
     * @param title 厅主动态表前缀
     * @param projectId 注单ID
     * @return 返点记录
     */
    List<UserDiffpointsEntity> selectByProjectId(@Param("title") String title, @Param("projectId") String projectId);

    /**
     * 更新已派返点为已撤销
     * @param title 厅主动态表前缀
     * @param projectId 注单ID
     * @return 更新行数
     */
    int cancelPaidByProjectId(@Param("title") String title, @Param("projectId") String projectId);

    /**
     * 更新未派返点为已撤单
     * @param title 厅主动态表前缀
     * @param projectId 注单ID
     * @param cancelStatus 撤单状态
     * @return 更新行数
     */
    int cancelUnpaidByProjectId(@Param("title") String title,
                                @Param("projectId") String projectId,
                                @Param("cancelStatus") Integer cancelStatus);
}




