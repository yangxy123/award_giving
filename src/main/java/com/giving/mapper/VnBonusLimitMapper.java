package com.giving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.VnBonusLimitEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VnBonusLimitMapper extends BaseMapper<VnBonusLimitEntity> {

    /**
     * 查询指定奖期号码累计奖金
     * @param title 厅主动态表前缀
     * @param opCode 商户代号
     * @param lotteryId 彩种ID
     * @param issue 奖期
     * @return 号码累计奖金列表
     */
    List<VnBonusLimitEntity> selectByOpLotteryIssue(@Param("title") String title,
                                                    @Param("opCode") String opCode,
                                                    @Param("lotteryId") Integer lotteryId,
                                                    @Param("issue") String issue);

    /**
     * 增加号码累计奖金
     * @param title 厅主动态表前缀
     * @param records 累计记录
     * @return 影响行数
     */
    int upsertSummary(@Param("title") String title,
                      @Param("records") List<VnBonusLimitEntity> records);
}
