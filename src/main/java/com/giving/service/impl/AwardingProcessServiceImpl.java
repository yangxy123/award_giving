package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.LotteryMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.DrawSourceReq;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:47
 */
@Service
public class AwardingProcessServiceImpl implements AwardingProcessService {

    @Autowired
    IssueInfoMapper issueInfoMapper;

    @Autowired
    RoomMasterMapper roomMasterMapper;

    @Autowired
    AwardGivingService awardGivingService;

    @Autowired
    LotteryMapper lotteryMapper;

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
            return;
        }
        if(!StringUtils.isEmpty(issueInfo.getCode()))
        {
            return;
        }
        //修改奖期
        issueInfo.setCode(req.getWinCode());
        issueInfo.setWriteTime(new Date());
        issueInfo.setStatusFetch(2);
        issueInfo.setStatusCode(2);
        issueInfo.setWriteId(0);
        issueInfoMapper.updateById(issueInfo);

        updateRoomsIssueInfo(issueInfo);

    }
    /**
     * 写入各平台商的平台商奖期表
     * @param issueInfo
     */
    public void updateRoomsIssueInfo(IssueInfoEntity issueInfo){
        //2.取得平台商信息表中的平台商前缀
        List<String> titles = roomMasterMapper.selectTitle();
        //3.把主奖期表的号码写入各平台商奖期表中
        issueInfoMapper.insertIssueToRooms(titles,issueInfo);
        //todo:4.号码写入后查询该期订单（cn007_projects），通过对应订单的玩法(method)进行验派
        new Thread(() -> {
            for (String title : titles) {
                NoticeReq n = new NoticeReq();
                n.setTitle(title);
                n.setTableName(title + "_issue_info");
                n.setIssue(issueInfo.getIssue());
                n.setCode(issueInfo.getCode());
                n.setLotteryId(issueInfo.getLotteryId());
                List<LotteryEntity> lotter18 = lotteryMapper.selectLotterIdIn18();
                //18--越南自开
                //泰国
                switch (issueInfo.getLotteryId().toString()){
                    case "212":
                    case "223":
                        //18--越南自开
                        awardGivingService.notice(n);
                        break;
                    case "242":

                        break;
                }



            }
        }).start();
        //  5.验派后出现的金额变化写入账变表(cn007_orders)，并修改用户余额(cn007_user_fund)
    }
}
