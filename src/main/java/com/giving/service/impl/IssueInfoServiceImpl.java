package com.giving.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.pagehelper.Page;
import com.giving.entity.IssueInfoEntity;
import com.giving.req.GetIssueInfoReq;
import com.giving.service.IssueInfoService;
import com.giving.mapper.IssueInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author zzby
* @description 针对表【issue_info(奖期信息)】的数据库操作Service实现
* @createDate 2026-01-04 12:18:11
*/
@Service
public class IssueInfoServiceImpl extends ServiceImpl<IssueInfoMapper, IssueInfoEntity>
    implements IssueInfoService{

    /**
     * 获取奖期信息
     * @param req
     * @return
     */
    @Override
    public Page<IssueInfoEntity> getIssueInfo(GetIssueInfoReq req) {
        return null;
    }
}




