package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户动态表Mapper
 */
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 根据用户ID查询厅主用户
     * @param title 厅主动态表前缀
     * @param userId 用户ID
     * @return 用户信息
     */
    UserEntity selectByUserId(@Param("title") String title, @Param("userId") String userId);

    int insertUsers(@Param("title") String title, @Param("users") List<UserEntity> users);

    int countAvailableUsers(@Param("title") String title);

    List<UserEntity> selectAvailableUsers(@Param("title") String title, @Param("limit") Integer limit);
}
