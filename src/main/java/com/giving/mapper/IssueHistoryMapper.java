package com.giving.mapper;

import com.giving.entity.IssueHistoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.IssueInfoEntity;
import com.giving.req.DrawSourceReq;
import org.apache.ibatis.annotations.Param;

/**
* @author zzby
* @description 针对表【issue_history(历史奖期)】的数据库操作Mapper
* @createDate 2026-01-25 16:16:57
* @Entity com.giving.entity.IssueHistory
*/
public interface IssueHistoryMapper extends BaseMapper<IssueHistoryEntity> {

    void updateOrInsert(@Param("req") DrawSourceReq req,@Param("issueInfo") IssueInfoEntity issueInfo);
}




