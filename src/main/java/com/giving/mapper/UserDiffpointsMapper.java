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
}




