package com.giving.mapper;

import com.giving.entity.IssueInfoEntity;
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
    /**
     * 批量写入正式注单临时表
     * @param title 厅主动态表前缀
     * @param projectsTmp 临时注单列表
     * @return 写入行数
     */
    int insertProjectsTmp(@Param("title") String title, @Param("projectsTmp") List<ProjectsTmpEntity> projectsTmp);

    void createData(@Param("uuids") List<String> uuidList, @Param("issue")IssueInfoEntity issue,@Param("titles") List<String> titles);

    void createIssueData(@Param("issue") IssueInfoEntity issue,@Param("titles") List<String> titles);
}




