package com.giving.mapper;

import com.giving.entity.IssueInfoEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author zzby
* @description 针对表【issue_info(奖期信息)】的数据库操作Mapper
* @createDate 2026-01-04 12:18:11
* @Entity com.giving.entity.IssueInfo
*/
public interface IssueInfoMapper extends BaseMapper<IssueInfoEntity> {
    void insertIssueToRooms(@Param("titles") List<String> titles, @Param("issueInfo") IssueInfoEntity issueInfo);
}




