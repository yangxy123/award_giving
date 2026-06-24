package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.BonusLimitUserInfoEntity;
import com.giving.entity.BonusLimitUserIssueInfoEntity;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BonusLimitMapper extends BaseMapper<BonusLimitUserInfoEntity> {

    /**
     * 查询彩种单注奖金限额
     * @param title 厅主动态表前缀
     * @param lotteryId 彩种ID
     * @param opCode 商户代号
     * @return 奖金限额，单位CNY
     */
    BigDecimal selectLotteryBonusLimit(@Param("title") String title,
                                       @Param("lotteryId") Integer lotteryId,
                                       @Param("opCode") String opCode);

    /**
     * 查询用户玩法奖金限额配置
     * @param title 厅主动态表前缀
     * @param userId 用户ID
     * @param methodIds 玩法ID列表
     * @return 用户玩法限额
     */
    List<BonusLimitUserInfoEntity> selectUserLimitConfigs(@Param("title") String title,
                                                          @Param("userId") String userId,
                                                          @Param("methodIds") List<Integer> methodIds);

    /**
     * 查询用户单期已累计预计奖金
     * @param title 厅主动态表前缀
     * @param userId 用户ID
     * @param lotteryId 彩种ID
     * @param methodId 玩法ID
     * @param codeName 信用玩法第四层
     * @param issue 奖期
     * @return 已累计预计奖金
     */
    BigDecimal selectUserIssuePrice(@Param("title") String title,
                                    @Param("userId") String userId,
                                    @Param("lotteryId") Integer lotteryId,
                                    @Param("methodId") Integer methodId,
                                    @Param("codeName") String codeName,
                                    @Param("issue") String issue);

    /**
     * 增加用户单期预计奖金累计
     * @param title 厅主动态表前缀
     * @param records 累计记录
     * @return 影响行数
     */
    int upsertUserIssuePrices(@Param("title") String title,
                              @Param("records") List<BonusLimitUserIssueInfoEntity> records);

    /**
     * 扣减用户单期预计奖金累计，供撤单或撤销派奖回滚使用
     * @param title 厅主动态表前缀
     * @param methodId 玩法ID
     * @param issue 奖期
     * @param codeName 信用玩法第四层
     * @param priceUser 扣减预计奖金
     * @return 影响行数
     */
    int subtractUserIssuePrice(@Param("title") String title,
                               @Param("methodId") Integer methodId,
                               @Param("issue") String issue,
                               @Param("codeName") String codeName,
                               @Param("priceUser") BigDecimal priceUser);
}
