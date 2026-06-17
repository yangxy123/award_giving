package com.giving.service.impl;
import com.giving.base.resp.ApiResp;
import com.giving.req.BetOrderReq;
import com.giving.service.BetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BetServiceImpl implements BetService {
    @Override
    public ApiResp<String> order(BetOrderReq req) {


        return ApiResp.sucess(req);
    }
}
