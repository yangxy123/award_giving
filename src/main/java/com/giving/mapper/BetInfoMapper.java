package com.giving.mapper;

import java.util.List;
import java.math.BigDecimal;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.BetInfoEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.req.NoticeReq;

/** 
* @author yangxy
* @version 创建时间：2025年12月30日 下午5:10:22 
*/
public interface BetInfoMapper extends BaseMapper<BetInfoEntity> {
	/**
	 * 批量写入正式注单
	 * @param title 厅主动态表前缀
	 * @param projects 注单列表
	 * @return 写入行数
	 */
	int insertProjects(@Param("title") String title, @Param("projects") List<BetInfoEntity> projects);

	/**
	 * 查询用户今日有效投注总额
	 * @param title 厅主动态表前缀
	 * @param userId 用户ID
	 * @return 今日投注总额
	 */
	BigDecimal sumTodayTotalPriceByUser(@Param("title") String title, @Param("userId") String userId);

	/**
	 * 查询用户指定彩种奖期玩法的有效投注数量
	 * @param title 厅主动态表前缀
	 * @param userId 用户ID
	 * @param lotteryId 彩种ID
	 * @param issue 奖期
	 * @param methodIds 玩法ID列表
	 * @return 投注数量
	 */
	Integer countByUserLotteryIssueMethods(@Param("title") String title,
										   @Param("userId") String userId,
										   @Param("lotteryId") Integer lotteryId,
										   @Param("issue") String issue,
										   @Param("methodIds") List<Integer> methodIds);

	/**
	 * 锁定并读取订单最新状态，避免并发任务使用过期状态重复操作钱包。
	 */
	BetInfoEntity selectProjectByIdForUpdate(@Param("title") String title,
											@Param("projectId") String projectId);

	/**
	 * 查询可撤销派奖的已派奖中奖注单。
	 */
	List<BetInfoEntity> selectAwardedProjectsForCancel(@Param("title") String title,
													   @Param("lotteryId") Long lotteryId,
													   @Param("issue") String issue,
													   @Param("projectId") String projectId);

	/**
	 * 查询整期需要还原到未开奖状态的有效注单。
	 */
	List<BetInfoEntity> selectIssueProjectsForCancel(@Param("title") String title,
													 @Param("lotteryId") Long lotteryId,
													 @Param("issue") String issue);

	/**
	 * 按本次目标注单锁定并读取可撤销派奖的最新状态。
	 */
	List<BetInfoEntity> selectAwardedProjectsByIdsForUpdate(@Param("title") String title,
															@Param("lotteryId") Long lotteryId,
															@Param("issue") String issue,
															@Param("projectIds") List<String> projectIds);

	/**
	 * 重置注单为未验奖、未派奖。
	 */
	int resetCancelAwardProject(@Param("title") String title,
								@Param("projectId") String projectId);

	/**
	 * 条件更新注单为已撤单
	 * @param title 厅主动态表前缀
	 * @param projectId 注单ID
	 * @param userId 用户ID
	 * @param isDeduct 真实扣款状态
	 * @param cancelType 撤单类型
	 * @return 更新行数
	 */
	int updateCancelStatus(@Param("title") String title,
						   @Param("projectId") String projectId,
						   @Param("userId") String userId,
						   @Param("isDeduct") Integer isDeduct,
						   @Param("cancelType") Integer cancelType);

	/**
	 * 还原注单返点状态
	 * @param title 厅主动态表前缀
	 * @param projectId 注单ID
	 * @return 更新行数
	 */
	int resetPointStatus(@Param("title") String title, @Param("projectId") String projectId);

    @Select({
		"<script>"
		+ "select project_id projectId from ${table} "
		+ "where project_id = #{where1} and ttt=#{where2}"
		+ "</script>"
	})
	/**
	 * 动态SQL示例（返回对象时，对象里的属性需要和查询字段一一对应，如果对应不上可以给查询出的字段取别名）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午8:37:45 
	* @param table 表名
	* @param param 查询参数
	 */
	public List<BetInfoEntity> test(@Param("table")String table,@Param("where1")String param,@Param("where2")String param1);
    
    @Update("UPDATE ${title}_projects set prize_status = 1,bonus_time = now(),is_deduct = 1,deduct_time=now() where issue = #{issue} and is_cancel = 0 and lottery_id = #{lotteryId}")
    /**
     * 修改当期中奖订单派奖状态和派奖时间
     * @param title 表头
     * @param issue 奖期
     */
    public void updatePrize(@Param("title")String title,@Param("issue")String issue,@Param("lotteryId") Long lotteryId);

	/**
	 * 取得未派奖订单
	 * @param noticeReq
	 * @return
	 */
	List<BetInfoEntity> selectListByNoticeReq(@Param("noticeReq") NoticeReq noticeReq);

	Integer countListByNoticeReq(@Param("noticeReq") NoticeReq noticeReq);

	/**
	 * 获取已中奖但尚未派奖的订单
	 * @param noticeReq
	 * @return
	 */
	List<BetInfoEntity> selectPendingAwardList(@Param("noticeReq") NoticeReq noticeReq);

	/**
	 * 只记录中奖结果，不派发奖金
	 * @param title
	 * @param projects
	 * @return
	 */
	int updateWinResult(@Param("title") String title, @Param("projects") List<BetInfoEntity> projects);

	/**
	 * 批量修改派奖状态
	 * @param title
	 * @param projects
	 */
	int updatePrizeStatus(@Param("title") String title, @Param("projects") List<BetInfoEntity> projects);

	/**
	 * 批量修改结算状态
	 * @param title
	 * @param projects
	 */
	int updateIsDeduct(@Param("title") String title, @Param("projects") List<BetInfoEntity> projects);
	/**
	 * 批量修改为已派发返点
	 * @param projects
	 * @param title
	 * @return
	 */
	int updatePoint(@Param("title") String title,@Param("project") List<BetInfoEntity> projects);
	/**
	 * 批量更新注单中奖状态 未中奖
	 * @param noticeReq
	 * @param notWinList
	 */
	void updateByNotWinList(@Param("noticeReq") NoticeReq noticeReq,@Param("notWinList") List<BetInfoEntity> notWinList);

	/**
	 * 获取所有尚未'真实扣款'的方案
	 * @return
	 */
	List<BetInfoEntity> checkProjects(@Param("title") String title, @Param("issue") TempIssueInfoEntity issue);

	/**
	 * 获取所有尚未'派發返點'的方案
	 * @param title
	 * @param issue
	 * @return
	 */
	List<BetInfoEntity> checkProjectsPoint(@Param("title") String title, @Param("issue") TempIssueInfoEntity issue);

	/**
	 * 修改为已扣款
	 * @param updateProject
	 * @return
	 */
    int updateDeduct(@Param("project") BetInfoEntity updateProject,@Param("title") String title);


	/**
	 * 修改注单状态为已中奖&已经派奖
	 * @param Project
	 * @param title
	 * @return
	 */
    int updateIsGetprize1(@Param("project") BetInfoEntity Project,@Param("title") String title);

	/**
	 * 修改注单状态为未中奖
	 * @param notWinList
	 * @param title
	 * @return
	 */
    int updateIsGetprize2(@Param("notWinList") List<BetInfoEntity> notWinList,@Param("title") String title);
    
    /**
	 * 修改指定奖期未验奖的订单为未中奖
	 * @param issue 奖期
	 * @param title 表头
	 * @return
	 */
    @Update("update ${title}_projects set is_getprize = 2,updated_at = now()"
    		+ "            where is_cancel = 0"
    		+ "            AND is_getprize = 0"
    		+ "            and issue = #{issue}"
    		+ "            and lottery_id = #{lotteryId}")
    int updateIsGetprizeTo2(@Param("issue") String issue,@Param("title") String title,@Param("lotteryId") Long lotteryId);
}
