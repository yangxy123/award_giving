package com.giving.mapper;

import com.giving.entity.TempIssueInfoEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.req.ManualDistributionReq;
import org.apache.ibatis.annotations.Param;

/**
* @author zzby
* @description 针对表【TEMP_issue_info(奖期信息)】的数据库操作Mapper
* @createDate 2026-01-20 14:37:21
* @Entity com.giving.entity.TempIssueInfo
*/
public interface TempIssueInfoMapper extends BaseMapper<TempIssueInfoEntity> {

    TempIssueInfoEntity selectByTitle(@Param("titles") String title, @Param("req") ManualDistributionReq req);
}




