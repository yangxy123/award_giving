package com.giving.mapper;

import com.giving.entity.IssueInfoEntity;
import com.giving.entity.IssueIdProducerEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.req.ManualDistributionReq;
import com.giving.req.UserNoteListReq;
import com.giving.resp.UserNoteListResp;
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

    IssueInfoEntity selectByTitle(@Param("titles") String title,@Param("req")  ManualDistributionReq req);


    IssueInfoEntity selectByLotteryIdAndIssue(@Param("lotteryId") Long lotteryId,@Param("issue") String issue);

    List<UserNoteListResp> selectUserNoteList(@Param("req") UserNoteListReq req,@Param("title") String title);

    /**
     * 查询指定彩种中已经存在的期号，用于自动生成奖期时过滤重复数据。
     */
    List<String> selectExistentIssues(@Param("lotteryId") Long lotteryId, @Param("issues") List<String> issues);

    /**
     * 写入 issue_id_producer，通过数据库自增 ID 生成全局唯一 issue_id。
     */
    int insertIssueIdProducer(IssueIdProducerEntity entity);

    /**
     * 将过滤后的新奖期批量写入 issue_info。
     */
    int batchInsertIssueInfo(@Param("list") List<IssueInfoEntity> list);
}




