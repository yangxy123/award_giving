package com.giving.service;

import com.github.pagehelper.Page;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.giving.req.BillSetApiReq;
import com.giving.req.UserNoteListReq;
import com.giving.resp.UserNoteListResp;

/**
* @author zzby
* @description 针对表【issue_info(奖期信息)】的数据库操作Service
* @createDate 2026-01-04 12:18:11
*/
public interface IssueInfoService extends IService<IssueInfoEntity> {
    /**
     * 获取奖期信息
     * @param req
     * @return
     */
    ApiResp<Page<UserNoteListResp>> userNoteList(UserNoteListReq req);

    /**
     * 设置盈利率
     * @param threshold
     * @return
     */
    ApiResp<String> nowThreshold(String threshold);

    /**
     * 设置开票机url
     * @param req
     * @return
     */
    ApiResp<String> setBillUrl(BillSetApiReq req);

    /**
     * 获取开票机url
     * @return
     */
    ApiResp<String> getBillUrl();
}
