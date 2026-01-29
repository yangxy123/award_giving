package com.giving.controller;

import com.giving.base.resp.ApiResp;
import com.giving.req.DrawSourceReq;
import com.giving.req.ListIssueReq;
import com.giving.req.ManualDistributionReq;
import com.giving.service.AwardingProcessService;
import com.giving.service.OPissueToolService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:22
 */
@RestController
@RequestMapping("/awardingProcess")
@Api(tags = "派奖流程")
public class AwardingProcessController {
    @Autowired
    private AwardingProcessService awardingProcessService;
    @Autowired
    private OPissueToolService opissueToolService;

    /**
     * 1.根据彩种奖期取到号码->写入主奖期表中（issue_info）
     * @param req
     */
    @PostMapping("/drawsource")
    @ApiOperation("录号派奖")
    public ApiResp<String> drawSource(@RequestBody @Valid DrawSourceReq req) {
        return awardingProcessService.drawSource(req);
    }

    /**
     * 1.根据彩种奖期取到号码->写入主奖期表中（issue_info）
     * @param req
     */
    @PostMapping("/reste")
    @ApiOperation("已经录号-重新验派")
    public ApiResp<String> resteDrawSource(@RequestBody @Valid ListIssueReq req) {
        return opissueToolService.resteDrawSource(req);
    }

    @PostMapping("/manualDistribution")
    @ApiOperation("手动厅组录号派奖")
    public ApiResp<String> manualDistribution(@RequestBody @Valid ManualDistributionReq req){
        return opissueToolService.manualDistribution(req);
    }

    @PostMapping("/manualdoCongealToReal")
    @ApiOperation("手动结算")
    public ApiResp<String> doCongealToReal(@RequestBody @Valid ManualDistributionReq req){
        return opissueToolService.doCongealToReal(req);
    }
    @PostMapping("/manualForceCongealToReal")
    @ApiOperation("强制-手动结算")
    public ApiResp<String> doForceCongealToReal(@RequestBody @Valid ManualDistributionReq req){
        return opissueToolService.doForceCongealToReal(req);
    }
    @PostMapping("/manualdoRabate")
    @ApiOperation("手动返点")
    public ApiResp<String> doRabate(@RequestBody @Valid ManualDistributionReq req){
        return opissueToolService.doRabate(req);
    }

}
