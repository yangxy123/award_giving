package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.management.DateSourceManagement;
import com.giving.mapper.*;
import com.giving.req.DrawSourceReq;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import com.giving.service.OrdersToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:47
 */
@Service
public class AwardingProcessServiceImpl implements AwardingProcessService {

    private static final Logger log = LoggerFactory.getLogger(AwardingProcessServiceImpl.class);
    @Autowired
    IssueInfoMapper issueInfoMapper;

    @Autowired
    AwardGivingService awardGivingService;

    @Autowired
    LotteryMapper lotteryMapper;

    @Autowired
    AwardGivingService awardService;

    @Autowired
    OrdersToolService ordersToolService;

    @Autowired
    private IssueHistoryMapper issueHistoryMapper;

    /**
     * 派奖流程
     * @param req
     */
    @Override
    public ApiResp<String> drawSource(DrawSourceReq req) {
        //判断是否存在奖期
        LambdaQueryWrapper<IssueInfoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IssueInfoEntity::getLotteryId,req.getLotteryId());
        queryWrapper.eq(IssueInfoEntity::getIssue,req.getIssue());
        IssueInfoEntity issueInfo = issueInfoMapper.selectOne(queryWrapper);
        //如果存在就修改奖期
        if(ObjectUtils.isEmpty(issueInfo)){
            log.info("========奖期不存在==========={}",req.getIssue());
            return ApiResp.paramError("奖期不存在"+req.getIssue());
        }
        if(!StringUtils.isEmpty(issueInfo.getCode())) {
            log.info("========Code存在,已经录号，不再重复录号==========={}",req.getIssue());
            return ApiResp.paramError("Code存在,已经录号，不再重复录号"+req.getIssue());
        }
        //修改奖期
        issueInfo.setCode(req.getWinCode());
        issueInfo.setWriteTime(new Date());
        issueInfo.setStatusFetch(2);
        issueInfo.setStatusCode(2);
        issueInfo.setWriteId(0);
        DateSourceManagement.flag.set("gs");
        issueInfoMapper.updateById(issueInfo);
        DateSourceManagement.flag.set("gc");

        //奖期历史记录
        if (req.getLotteryId() == 130 || req.getLotteryId() == 132 || req.getLotteryId() == 281) {
            req.setWinCode(req.getWinCode().replace(",",""));
        }
        issueHistoryMapper.updateOrInsert(req,issueInfo);

        //测试时自动向数据库插入下一期数据
//        this.FakeIssue(issueInfo);

        ordersToolService.updateRoomsIssueInfo(issueInfo);

        return ApiResp.sucess();
    }



    /**
     *根据采种方法执行对应的验证流程
     * @param roomMaster
     * @param issueInfo
     */
    @Override
    public void lotteryDraw(RoomMasterEntity roomMaster,IssueInfoEntity issueInfo){
        LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
        new Thread(() -> {
            NoticeReq n = new NoticeReq();
            n.setRoomMaster(roomMaster);
            n.setTableName(roomMaster.getTitle()+"_issue_info");
            n.setTitle(roomMaster.getTitle());
            n.setIssue(issueInfo.getIssue());
            n.setCode(issueInfo.getCode());
            n.setLotteryId(issueInfo.getLotteryId());
            switch (lottery.getFunctionType()){
                case "VN_S":
                case "VN_C": //18--越南自开
                    awardGivingService.notice(n);
                    break;
                case "VN_N"://28-组 越南北部
                    awardGivingService.noticeNorth(n);
                    break;
                case "TH":      //泰国彩
                case "TH_30S":  //泰国分分彩
                    awardGivingService.noticeTh(n);
                    break;
                case "LA":  //老挝彩
                case "MY":  //马来西亚彩
                    awardGivingService.noticeLw(n);
                    break;
//                case "FC3D":  //加拿大1分3D  亚洲30秒3D
                case "K3":  //亚洲30秒快3 亚洲1分快3  澳洲5分快3
                    awardGivingService.noticeKs(n);
                    break;
            }
        }).start();

    }

    //测试数据生成
    private void FakeIssue(IssueInfoEntity issueInfo){

        new Thread(() ->{
            LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
            if(!lottery.getFunctionType().equals("K3")) {
                awardService.createData(Integer.parseInt(issueInfo.getLotteryId().toString()));
            }
        }).start();
    }
}
