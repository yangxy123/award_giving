package com.giving.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.enums.RedisKeyEnums;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.BillSetApiReq;
import com.giving.req.FakeBetReq;
import com.giving.req.UserNoteListReq;
import com.giving.resp.UserNoteListResp;
import com.giving.service.IssueInfoService;
import com.giving.mapper.IssueInfoMapper;
import com.giving.util.HttpUtil;
import com.giving.util.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.giving.management.DateSourceManagement;
import springfox.documentation.spring.web.json.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author zzby
* @description 针对表【issue_info(奖期信息)】的数据库操作Service实现
* @createDate 2026-01-04 12:18:11
*/
@Service
public class IssueInfoServiceImpl extends ServiceImpl<IssueInfoMapper, IssueInfoEntity>
    implements IssueInfoService{
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private HttpUtil httpUtil;
    @Autowired
    private RedisUtils redisUtils;

    /**
     * 获取奖期信息
     * @param req
     * @return
     */
    @Override
    public ApiResp<Page<UserNoteListResp>> userNoteList(UserNoteListReq req) {
        String title = "";
        if(req.getMasterId() != null && !(req.getMasterId().equals(""))){
            title = roomMasterMapper.selectTitleById(req.getMasterId());
        }
        PageHelper.startPage(req.getPageNo(),req.getPageSize());
        List<UserNoteListResp> list = issueInfoMapper.selectUserNoteList(req,title);
        Page<UserNoteListResp> page = (Page<UserNoteListResp>) list;
        return ApiResp.page(page);
    }

    @Override
    public ApiResp<String> nowThreshold(String threshold) {
        String url = (String) redisUtils.get(RedisKeyEnums.BILL_API_URL.key);
        //String url = "http://192.168.124.17:8991/merchant/nowthreshold";
        if(url == null || url.equals("")){
            return ApiResp.paramError("未设置开票机api地址");
        }
        httpUtil.doJsonPost(url,"{\"threshold\": "+threshold+"}",null);
        return ApiResp.sucess();
    }

    /**
     * 设置开票机url
     * @param req
     * @return
     */
    @Override
    public ApiResp<String> setBillUrl(BillSetApiReq req) {
        redisUtils.set(RedisKeyEnums.BILL_API_URL.key,req.getUrl());
        return ApiResp.sucess();
    }

    /**
     * 获取开票机url
     * @return
     */
    @Override
    public ApiResp<String> getBillUrl() {
        String url = (String) redisUtils.get(RedisKeyEnums.BILL_API_URL.key);
        return ApiResp.sucess(url);
    }
}




