package com.giving.controller;

import com.github.pagehelper.Page;
import com.giving.base.resp.ApiResp;
import com.giving.req.UserNoteListReq;
import com.giving.resp.UserNoteListResp;
import com.giving.service.IssueInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    @ApiOperation("用户注单查询")
    public ApiResp<Page<UserNoteListResp>> userNoteList(@RequestBody @Valid UserNoteListReq req) {
        return issueInfoService.userNoteList(req);
    }

    @PostMapping("/nowthreshold/{threshold}")
    @ApiOperation("设置当前盈利率")
    public ApiResp<String> nowthreshold(@PathVariable("threshold") String threshold) {
        return issueInfoService.nowthreshold(threshold);
    }
}
