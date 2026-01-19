package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.LotteryMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.DrawSourceReq;
import com.giving.req.ListIssueReq;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    RoomMasterMapper roomMasterMapper;

    @Autowired
    AwardGivingService awardGivingService;

    @Autowired
    LotteryMapper lotteryMapper;

    @Autowired
    AwardGivingService awardService;

    /**
     * 派奖流程
     * @param req
     */
    @Override
    public void drawSource(DrawSourceReq req) {
        //判断是否存在奖期
        LambdaQueryWrapper<IssueInfoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IssueInfoEntity::getLotteryId,req.getLotteryId());
        queryWrapper.eq(IssueInfoEntity::getIssue,req.getIssue());
        IssueInfoEntity issueInfo = issueInfoMapper.selectOne(queryWrapper);
        //如果存在就修改奖期
        if(ObjectUtils.isEmpty(issueInfo)){
            log.info("========奖期不存在===========");
            return;
        }
        if(!StringUtils.isEmpty(issueInfo.getCode()))
        {
            log.info("========Code不存在===========");
            return;
        }
        //修改奖期
        issueInfo.setCode(req.getWinCode());
        issueInfo.setWriteTime(new Date());
        issueInfo.setStatusFetch(2);
        issueInfo.setStatusCode(2);
        issueInfo.setWriteId(0);
        issueInfoMapper.updateById(issueInfo);

//        LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
        new Thread(() ->{
            LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
            if(lottery.getFunctionType().equals("VN_S") || lottery.getFunctionType().equals("VN_C")) {
                awardService.createData(Integer.parseInt(issueInfo.getLotteryId().toString()));
            }

        }).start();
        updateRoomsIssueInfo(issueInfo);
    }

    /**
     *
     * @param req
     */
    @Override
    public void resteDrawSource(ListIssueReq req){
        //判断是否存在奖期
        LambdaQueryWrapper<IssueInfoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(IssueInfoEntity::getLotteryId,req.getLotteryId());
        queryWrapper.eq(IssueInfoEntity::getIssue,req.getIssue());
        IssueInfoEntity issueInfo = issueInfoMapper.selectOne(queryWrapper);

        updateRoomsIssueInfo(issueInfo);
    }
    /**
     * 写入各平台商的平台商奖期表
     * @param issueInfo
     */
    public void updateRoomsIssueInfo(IssueInfoEntity issueInfo){
        //2.取得平台商信息表中的平台商前缀
        List<RoomMasterEntity> roomMasters = roomMasterMapper.selectTitle();

        //3.把主奖期表的号码写入各平台商奖期表中
        issueInfoMapper.insertIssueToRooms(roomMasters.stream().map(RoomMasterEntity::getTitle).collect(Collectors.toList()),issueInfo);
        //todo:4.号码写入后查询该期订单（cn007_projects），通过对应订单的玩法(method)进行验派
        for (RoomMasterEntity roomMaster : roomMasters) {
            lotteryDraw(roomMaster,issueInfo);
        }
        //  5.验派后出现的金额变化写入账变表(cn007_orders)，并修改用户余额(cn007_user_fund)
    }

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
            //18--越南自开
            //泰国
            switch (lottery.getFunctionType()){
                case "VN_S":
                case "VN_C":
                    //18--越南自开
                    awardGivingService.notice(n);
                    break;
                case "VN_N":
//                        28-组
                    awardGivingService.noticeNorth(n);
                    break;
            }
        }).start();

    }
}
