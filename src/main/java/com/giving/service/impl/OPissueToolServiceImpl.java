package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.req.ManualDistributionReq;
import com.giving.service.AwardingProcessService;
import com.giving.service.OPissueToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:11
 */
@Service
public class OPissueToolServiceImpl implements OPissueToolService {
    private static final Logger log = LoggerFactory.getLogger(AwardingProcessServiceImpl.class);
    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private AwardingProcessService awardingProcessService;

    //public AwardingProcessServiceImpl awardingProcess = new AwardingProcessServiceImpl();

    @Override
    public ApiResp<String> manualDistribution(ManualDistributionReq req) {
        LambdaQueryWrapper<IssueInfoEntity> wrapper = new LambdaQueryWrapper<IssueInfoEntity>();
        wrapper.eq(IssueInfoEntity::getLotteryId,req.getLotteryId());
        wrapper.eq(IssueInfoEntity::getIssue,req.getIssue());
        IssueInfoEntity issueInfoEntity = issueInfoMapper.selectOne(wrapper);
        if(issueInfoEntity.getCode() == null || issueInfoEntity.getCode().equals("")){
            log.info("========未录号===========");
            return ApiResp.paramError("未录号");
        }

        if(req.getMasterId() != null){
            LambdaQueryWrapper<RoomMasterEntity> wrapper1 = new LambdaQueryWrapper<>();
            wrapper1.eq(RoomMasterEntity::getMasterId,req.getMasterId());
            RoomMasterEntity roomMasterEntity = roomMasterMapper.selectOne(wrapper1);
            if(ObjectUtils.isEmpty(roomMasterEntity)){
                //return 厅组id错误，厅组不存在
                log.info("厅组id错误，厅组不存在");
                return ApiResp.paramError("厅组id错误，厅组不存在");
            }

            IssueInfoEntity issueInfo = issueInfoMapper.selectByTitle(roomMasterEntity.getTitle(),req);
            if(issueInfo.getCode() == null || issueInfo.getCode().equals("")){
                log.info("厅组id错误，厅组未录号");
                List<String> titles = new ArrayList<>();
                titles.add(roomMasterEntity.getTitle());
                issueInfoMapper.insertIssueToRooms(titles,issueInfoEntity);
                issueInfo.setCode(issueInfoEntity.getCode());
            }else{
                log.info("厅组id错误，厅组已录号");
                return ApiResp.paramError("厅组id错误，厅组已录号");
            }
            awardingProcessService.lotteryDraw(roomMasterEntity,issueInfo);
        }

        return ApiResp.sucess();

    }
}
