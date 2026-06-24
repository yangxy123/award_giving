package com.giving.service.impl;

import com.giving.entity.BetInfoEntity;
import com.giving.entity.BonusLimitUserIssueInfoEntity;
import com.giving.entity.OrdersEntity;
import com.giving.entity.ProjectsTmpEntity;
import com.giving.entity.TempUserDiffpointsEntity;
import com.giving.entity.VnBonusLimitEntity;
import com.giving.mapper.BetInfoMapper;
import com.giving.mapper.BonusLimitMapper;
import com.giving.mapper.OrdersMapper;
import com.giving.mapper.ProjectsTmpMapper;
import com.giving.mapper.UserDiffpointsMapper;
import com.giving.mapper.UserFundMapper;
import com.giving.mapper.VnBonusLimitMapper;
import com.giving.service.BetOrderTxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BetOrderTxServiceImpl implements BetOrderTxService {

    @Autowired
    private BetInfoMapper betInfoMapper;
    @Autowired
    private ProjectsTmpMapper projectsTmpMapper;
    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private UserDiffpointsMapper userDiffpointsMapper;
    @Autowired
    private UserFundMapper userFundMapper;
    @Autowired
    private BonusLimitMapper bonusLimitMapper;
    @Autowired
    private VnBonusLimitMapper vnBonusLimitMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void createOrder(String title, String userId, int walletType, BigDecimal totalAmount,
                            List<BetInfoEntity> projects, List<ProjectsTmpEntity> projectsTmp,
                            List<OrdersEntity> orders, List<TempUserDiffpointsEntity> userDiffpoints,
                            List<BonusLimitUserIssueInfoEntity> userIssueLimits,
                            List<VnBonusLimitEntity> vnBonusLimits) {
        if (betInfoMapper.insertProjects(title, projects) != projects.size()) {
            throw new IllegalStateException("写入注单失败");
        }
        if (projectsTmpMapper.insertProjectsTmp(title, projectsTmp) != projectsTmp.size()) {
            throw new IllegalStateException("写入注单临时表失败");
        }
        if (ordersMapper.addOrdersListAll(orders, title) != orders.size()) {
            throw new IllegalStateException("写入账变失败");
        }
        if (userDiffpointsMapper.insertUserDiffpoints(title, userDiffpoints) != userDiffpoints.size()) {
            throw new IllegalStateException("写入返点记录失败");
        }
        if (userIssueLimits != null && !userIssueLimits.isEmpty()) {
            bonusLimitMapper.upsertUserIssuePrices(title, userIssueLimits);
        }
        if (vnBonusLimits != null && !vnBonusLimits.isEmpty()) {
            vnBonusLimitMapper.upsertSummary(title, vnBonusLimits);
        }
        if (userFundMapper.freezeBetAmount(title, userId, walletType, totalAmount) <= 0) {
            throw new IllegalStateException("余额不足");
        }
    }
}
