package com.giving.controller;

import com.giving.base.resp.ApiResp;
import com.giving.req.BetOrderReq;
import com.giving.resp.BetOrderResp;
import com.giving.service.BetService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/game")
@Api(tags = "投注相关")
public class BetController {
    @Autowired
    private BetService betService;

    @PostMapping({"/order", "/order/{jwtToken}"})
    @ApiOperation("投注")
    public ApiResp<BetOrderResp> order(@RequestBody @Valid BetOrderReq req) {
        return betService.order(req);
    }
}
