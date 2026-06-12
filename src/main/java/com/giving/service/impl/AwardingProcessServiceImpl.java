package com.giving.service.impl;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.LotteryEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.management.DateSourceManagement;
import com.giving.mapper.IssueHistoryMapper;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.LotteryMapper;
import com.giving.req.DrawSourceReq;
import com.giving.req.NoticeReq;
import com.giving.service.AwardGivingService;
import com.giving.service.AwardingProcessService;
import com.giving.service.OrdersToolService;
import com.giving.util.RedisUtils;

import lombok.extern.slf4j.Slf4j;


/**
 * @author zzby
 * @version 创建时间： 2026/1/4 上午11:47
 */
@Slf4j
@Service
public class AwardingProcessServiceImpl implements AwardingProcessService {
    private static final String AWARD_PROCESS_LOCK_PREFIX = "award:process:";
    private static final long AWARD_PROCESS_LOCK_EXPIRE_SECONDS = 3600L;

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
    @Autowired
    private RedisUtils redisUtils;

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
            log.info("彩种{}========奖期不存在==========={}",req.getLotteryId(), req.getIssue());
            return ApiResp.paramError("奖期不存在"+req.getIssue());
        }
        if(!StringUtils.isEmpty(issueInfo.getCode())) {
            log.info("彩种{}========开奖号码已存在，已经录号直接退出==========={}",req.getLotteryId(),req.getIssue());
//            ordersToolService.updateRoomsIssueInfo(issueInfo);
            return ApiResp.paramError("开奖号码已存在，已经录号"+req.getIssue());
        }
        //修改奖期
        issueInfo.setCode(req.getWinCode());
        issueInfo.setWriteTime(new Date());
        issueInfo.setStatusFetch(2);
        issueInfo.setStatusCode(2);
        issueInfo.setWriteId(0);
        try {
            DateSourceManagement.use("gs");
            issueInfoMapper.updateById(issueInfo);
        } finally {
            DateSourceManagement.clear();
        }

        //奖期历史记录
        if (req.getLotteryId() == 130 || req.getLotteryId() == 132 || req.getLotteryId() == 281) {
            req.setWinCode(req.getWinCode().replace(",",""));
        }
        //存入历史奖期
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
        log.info("后台验奖派奖开始，厅主ID={}，彩种ID={}，奖期={}",
                roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
        LotteryEntity lottery = lotteryMapper.selectById(issueInfo.getLotteryId());
        new Thread(() -> {
            String processLockKey = getAwardProcessLockKey(roomMaster, issueInfo);
            String processLockToken = UUID.randomUUID().toString();
            if (!redisUtils.setLock(processLockKey, processLockToken, AWARD_PROCESS_LOCK_EXPIRE_SECONDS)) {
                log.warn("相同厅主、彩种和奖期的后台验派任务正在执行，本次重复任务跳过，厅主ID={}，彩种ID={}，奖期={}",
                        roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
                return;
            }
            try {
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
//                  case "FC3D":  //加拿大1分3D  亚洲30秒3D
                    case "K3":  //亚洲30秒快3 亚洲1分快3  澳洲5分快3
                        awardGivingService.noticeKs(n);
                        break;
                }
                log.info("后台验奖派奖完成，厅主ID={}，彩种ID={}，奖期={}",
                        roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
            } catch (Exception e) {
                log.error("后台验奖派奖失败，厅主ID={}，彩种ID={}，奖期={}",
                        roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue(), e);
            } finally {
                if (!redisUtils.unlock(processLockKey, processLockToken)) {
                    log.warn("后台验派任务锁未释放或已过期，厅主ID={}，彩种ID={}，奖期={}",
                            roomMaster.getMasterId(), issueInfo.getLotteryId(), issueInfo.getIssue());
                }
            }
        }).start();
    }

    private String getAwardProcessLockKey(RoomMasterEntity roomMaster, IssueInfoEntity issueInfo) {
        return AWARD_PROCESS_LOCK_PREFIX
                + roomMaster.getTitle() + ":"
                + issueInfo.getLotteryId() + ":"
                + issueInfo.getIssue();
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
