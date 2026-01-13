package com.giving.mapper;

import com.giving.entity.ProjectsTmpEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author zzby
* @description 针对表【TEMP_projects_tmp】的数据库操作Mapper
* @createDate 2026-01-11 15:25:48
* @Entity com.giving.entity.ProjectsTmp
*/
public interface ProjectsTmpMapper extends BaseMapper<ProjectsTmpEntity> {

    void createData(@Param("uuids") List<String> uuidList);
}




