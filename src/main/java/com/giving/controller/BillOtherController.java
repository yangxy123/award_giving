package com.giving.controller;

import com.giving.base.resp.ApiResp;
import com.giving.enums.RedisKeyEnums;
import com.giving.req.BillOpenReq;
import com.giving.req.BillSetApiReq;
import com.giving.service.BillOtherService;
import com.giving.util.RedisUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;

/**
 * @author zzby
 * @version 创建时间： 2026/1/30 下午3:56
 */
@RestController
@RequestMapping("/bill")
@Api(tags = "开票机相关")
public class BillOtherController {
    private static final Logger log = LoggerFactory.getLogger(BillOtherController.class);
    @Autowired
    private BillOtherService billOtherService;
    @Autowired
    private RedisUtils redisUtils;

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

    @GetMapping("/test")
    @ApiOperation("test")
    public ApiResp<String> test() {
        String nowString = "20260130";
        String priceValue = redisUtils.rawHget(RedisKeyEnums.C_PROFIT_DATA.key, nowString+"_price");
        String bonusValue = redisUtils.rawHget(RedisKeyEnums.C_PROFIT_DATA.key, nowString+"_bonus");
        if(priceValue == null || bonusValue == null){
            return ApiResp.paramError("d");
        }
        //（总投注-总派奖+总反点）/总投注
        BigDecimal price = new BigDecimal(priceValue);
        BigDecimal bonus = new BigDecimal(bonusValue);
        BigDecimal t = (price.subtract(bonus)).divide(price,2);
        log.info("\n平台盈亏:( {} - {} ) / {} = {}",price,bonus,price,t);
        return ApiResp.sucess();
    }
}
