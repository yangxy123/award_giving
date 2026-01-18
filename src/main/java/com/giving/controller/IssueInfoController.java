package com.giving.controller;

import com.github.pagehelper.Page;
import com.giving.entity.IssueInfoEntity;
import com.giving.req.DrawSourceReq;
import com.giving.req.GetIssueInfoReq;
import com.giving.service.IssueInfoService;
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
 * @version 创建时间： 2026/1/18 下午2:34
 */
@RestController
@RequestMapping("/issueInfo")
@Api(tags = "奖期信息相关")
public class IssueInfoController {
    @Autowired
    private IssueInfoService issueInfoService;

    @PostMapping("/info")
    @ApiOperation("获取奖期信息")
    public Page<IssueInfoEntity> getIssueInfo(@RequestBody @Valid GetIssueInfoReq req) {
        return issueInfoService.getIssueInfo(req);
    }
}
