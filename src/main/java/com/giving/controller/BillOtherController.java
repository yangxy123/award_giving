package com.giving.controller;

import com.giving.base.resp.ApiResp;
import com.giving.req.BillOpenReq;
import com.giving.req.BillSetApiReq;
import com.giving.service.BillOtherService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * @author zzby
 * @version 创建时间： 2026/1/30 下午3:56
 */
@RestController
@RequestMapping("/bill")
@Api(tags = "开票机相关")
public class BillOtherController {
    @Autowired
    private BillOtherService billOtherService;

    @PostMapping("/setBillApi")
    @ApiOperation(value = "设置开票机Api地址",notes = "eg: http://192.168.124.17:8991")
    public ApiResp<String> setBillUrl(@RequestBody @Valid BillSetApiReq req) {
        return billOtherService.setBillUrl(req);
    }

    @PostMapping("/getBillApi")
    @ApiOperation("获取开票机Api地址")
    public ApiResp<String> getBillUrl() {
        return billOtherService.getBillUrl();
    }

    @PostMapping("/nowthreshold/{threshold}")
    @ApiOperation("向开票机发送盈利率")
    public ApiResp<String> nowThreshold(@PathVariable("threshold") String threshold) {
        return billOtherService.nowThreshold(threshold);
    }

    @PostMapping("/open")
    @ApiOperation("open")
    public ApiResp<String> open(@RequestBody @Valid BillOpenReq req) {
        return billOtherService.open(req);
    }
}
