package com.giving.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.FakeBetReq;
import com.giving.req.UserNoteListReq;
import com.giving.resp.UserNoteListResp;
import com.giving.service.IssueInfoService;
import com.giving.mapper.IssueInfoMapper;
import com.giving.util.HttpUtil;
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
    public ApiResp<String> nowthreshold(String threshold) {
        String url = "http://192.168.124.17:8991/merchant/nowthreshold";
        httpUtil.doJsonPost(url,"{\"threshold\": "+threshold+"}",null);
        return ApiResp.sucess();
    }
    @Override
    public ApiResp<String> AutoBet(FakeBetReq req){
        try{
            String url = "http://192.168.124.17:8991/merchant/nowthreshold";
            Map<String,String> map = new HashMap<>();
            map.put("","");
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(map);
            httpUtil.doJsonPost(url, json,null);
            return ApiResp.sucess();
        } catch (Exception e) {
            return ApiResp.bussError(e.getMessage());
        }

    }

    @Override
    public ApiResp<Integer> test() {
        DateSourceManagement.flag.set("gs");
        long i = issueInfoMapper.selectCount(null);
        return ApiResp.sucess(i);
    }
}




