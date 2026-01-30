package com.giving.service.impl;

import com.alibaba.druid.support.json.JSONUtils;
import com.alibaba.fastjson.JSON;
import com.giving.base.resp.ApiResp;
import com.giving.enums.RedisKeyEnums;
import com.giving.req.BillOpenReq;
import com.giving.req.BillSetApiReq;
import com.giving.service.BillOtherService;
import com.giving.util.HttpUtil;
import com.giving.util.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author zzby
 * @version 创建时间： 2026/1/30 下午3:56
 */
@Service
public class BillOtherServiceImpl implements BillOtherService {
    @Autowired
    private HttpUtil httpUtil;
    @Autowired
    private RedisUtils redisUtils;
    @Override
    public ApiResp<String> nowThreshold(String threshold) {
        //eg:"http://192.168.124.17:8991/merchant/nowthreshold";
        String basePath = "/merchant/nowthreshold";
        String url = (String) redisUtils.get(RedisKeyEnums.BILL_API_URL.key);
        if(url == null || url.equals("")){
            return ApiResp.paramError("未设置开票机api地址");
        }
        httpUtil.doJsonPost(url+basePath,"{\"threshold\": "+threshold+"}",null);
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

    /**
     * open
     * @param req
     * @return
     */
    @Override
    public ApiResp<String> open(BillOpenReq req) {
        String basePath = "/open";
        String url = (String) redisUtils.get(RedisKeyEnums.BILL_API_URL.key);
        if(url == null || url.equals("")){
            return ApiResp.paramError("未设置开票机api地址");
        }
        String body = httpUtil.doJsonPost(url+basePath, JSON.toJSONString(req),null).getBody();
        return ApiResp.sucess(body);
    }
}
