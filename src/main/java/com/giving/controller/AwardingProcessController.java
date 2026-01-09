package com.giving.controller;

import com.giving.req.DrawSourceReq;
import com.giving.service.AwardingProcessService;
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

    /**
     * 1.根据彩种奖期取到号码->写入主奖期表中（issue_info）
     * @param req
     */
    @PostMapping("/drawsource")
    @ApiOperation("修改奖期")
    public void drawSource(@RequestBody @Valid DrawSourceReq req) {
        awardingProcessService.drawSource(req);
    }

}
