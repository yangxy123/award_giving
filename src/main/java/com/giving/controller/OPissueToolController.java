package com.giving.controller;

import com.giving.base.resp.ApiResp;
import com.giving.req.ManualDistributionReq;
import com.giving.service.OPissueToolService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:07
 */
@RestController
@RequestMapping("/OPissue")
@Api(tags = "开奖工具")
public class OPissueToolController {

    @Autowired
    private OPissueToolService opissueToolService;

    @PostMapping("/manualDistribution")
    @ApiOperation("手动厅组录号派奖")
    public ApiResp<String> manualDistribution(ManualDistributionReq req){
        return opissueToolService.manualDistribution(req);
    }

    @PostMapping("/doCongealToReal")
    @ApiOperation("手动结算")
    public ApiResp<String> doCongealToReal(ManualDistributionReq req){
        return opissueToolService.manualDistribution(req);
    }
}
