package com.giving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.IssueInfoEntity;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.IssueInfoMapper;
import com.giving.mapper.RoomMasterMapper;
import com.giving.mapper.TempIssueInfoMapper;
import com.giving.req.ManualDistributionReq;
import com.giving.service.AwardingProcessService;
import com.giving.service.OPissueToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * @author zzby
 * @version 创建时间： 2026/1/18 下午3:11
 */
@Service
@Transactional
public class OPissueToolServiceImpl implements OPissueToolService {
    private static final Logger log = LoggerFactory.getLogger(AwardingProcessServiceImpl.class);
    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private IssueInfoMapper issueInfoMapper;
    @Autowired
    private AwardingProcessService awardingProcessService;
    @Autowired
    private TempIssueInfoMapper tempIssueInfoMapper;
    @Autowired
    private BetInfoMapper betInfoMapper;

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

    @Override
    public ApiResp<String> doCongealToReal(ManualDistributionReq req) {
        try{
            LambdaQueryWrapper<RoomMasterEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RoomMasterEntity::getMasterId,req.getMasterId());
            RoomMasterEntity roomMasterEntity = roomMasterMapper.selectOne(wrapper);
            if(ObjectUtils.isEmpty(roomMasterEntity)){
                throw new RuntimeException("[厅主不存在] 厅主ID:"+req.getMasterId());
            }
            TempIssueInfoEntity issueInfo = tempIssueInfoMapper.selectByTitle(roomMasterEntity.getTitle(),req);
            if (ObjectUtils.isEmpty(issueInfo)){
                throw new RuntimeException("[厅主奖期不存在] 厅主ID:"+req.getMasterId()+"奖期："+req.getIssue());
            }
            if (issueInfo.getStatusDeduct() == 2){
                throw new RuntimeException("真實扣款已完成 status_deduct=2");
            }
            //修改為真實扣款進行中
            issueInfo.setStatusDeduct(1);
            if (tempIssueInfoMapper.updateById(issueInfo) != 1){
                throw new RuntimeException("修改奖期为真实扣款中失败");
            }
            // 获取所有尚未'真实扣款'的方案
            List<BetInfoEntity> projects = betInfoMapper.checkProjects(roomMasterEntity.getTitle(), issueInfo);
            //如果获取的结果集为空, 则表示当前奖期已全部'真实扣款'完成. 更新状态值
            if (ObjectUtils.isEmpty(projects)){
                issueInfo.setStatusDeduct(2);
                if (tempIssueInfoMapper.updateById(issueInfo) != 1){
                    throw new RuntimeException("修改奖期为真实扣款完成失败");
                }
            }

            List<String> userIds = projects.stream().map(BetInfoEntity::getUserId).collect(Collectors.toList());
            //锁定用户资金
            betInfoMapper.doLockUserFund(roomMasterEntity.getTitle(), userIds,4);




            //真實扣款 - 最後確認
            List<BetInfoEntity> projects2 = betInfoMapper.checkProjects(roomMasterEntity.getTitle(), issueInfo);
            if (ObjectUtils.isEmpty(projects2)){
                issueInfo.setStatusDeduct(2);
                if (tempIssueInfoMapper.updateById(issueInfo) != 1){
                    throw new RuntimeException("修改奖期为真实扣款完成失败");
                }
            }
            return ApiResp.sucess();
        } catch (RuntimeException e) {
            return ApiResp.paramError(e.getMessage());
        }
    }

    public boolean doLockUserFund(List<String> userIds,boolean bIsLocked,Integer sWalletType,String lockAction){
        try {

            // TRUE : 上鎖 ； FALSE : 解鎖
            // 钱包类别
            // 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款, 5: 单注验派
            int sNowIsLock    = (bIsLocked) ? 0 : 1;
            int sToIsLock     = (bIsLocked) ? 1 : 0;
            int iWalletType = (int)sWalletType;
            int iAffectNumber = (iWalletType == 0)? 6:1;
            String sLockAction  = lockAction+((bIsLocked) ? " 上锁" : " 解锁");// TRUE : 上鎖 ; FALSE : 解鎖

            Map<String,String> aConditions = new HashMap<>();
            aConditions.put("userid","$sUserId");
            aConditions.put("islocked",String.valueOf(sNowIsLock));
            if (iWalletType > 0) {
                aConditions.put("wallet_type",String.valueOf(sWalletType));
            }

            Map<String,String> data = new HashMap<>();
            data.put("islocked",String.valueOf(sToIsLock));
            data.put("lock_action",sLockAction);
//                    'updated_at' => Carbon::now()->toDateTimeString(),

            if ( ! (($this->model->where($aConditions)->update($data) >= $iAffectNumber))) {
                if ($bIsLocked) {
                    throw new \Exception($sUserId."-".$sLockAction."失敗");
                } else {
                    DB::rollBack();
                    return true;
                }
            }
            DB::commit();
            return true;
        }catch (Exception e){
            return false;
        }
    }
}
