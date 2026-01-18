package com.giving.service;

import com.github.pagehelper.Page;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.giving.req.UserNoteListReq;
import com.giving.resp.UserNoteListResp;

/**
* @author zzby
* @description 针对表【issue_info(奖期信息)】的数据库操作Service
* @createDate 2026-01-04 12:18:11
*/
public interface IssueInfoService extends IService<IssueInfoEntity> {

    ApiResp<Page<UserNoteListResp>> userNoteList(UserNoteListReq req);
}
