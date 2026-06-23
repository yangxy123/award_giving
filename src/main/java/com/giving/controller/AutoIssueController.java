package com.giving.controller;

import com.giving.base.resp.ApiResp;
import com.giving.task.AutoIssueTask;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/autoIssue")
@Api(tags = "AutoIssue")
public class AutoIssueController {

    @Autowired
    private AutoIssueTask autoIssueTask;

    @PostMapping("/run")
    @ApiOperation("manual run auto issue")
    public ApiResp<String> run() {
        // 手动触发自动奖期生成，方便测试时不等待定时任务执行。
        log.info("manual run auto issue task");
        autoIssueTask.scanAutoIssue();
        return ApiResp.sucess("auto issue task executed");
    }
}
