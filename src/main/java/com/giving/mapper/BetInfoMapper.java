package com.giving.mapper;

import java.util.List;

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
	 * 锁定并读取订单最新状态，避免并发任务使用过期状态重复操作钱包。
	 */
	BetInfoEntity selectProjectByIdForUpdate(@Param("title") String title,
											@Param("projectId") String projectId);

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
     * 淇敼褰撳墠鎵规璁㈠崟娲惧鐘舵€佸拰娲惧鏃堕棿
     */
    int updatePrizeByProjects(@Param("title") String title, @Param("projects") List<BetInfoEntity> projects);

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

    /**
     * 淇敼鎸囧畾鎵规鏈獙濂栫殑璁㈠崟涓烘湭涓
     */
    int updateIsGetprizeTo2ByProjects(@Param("title") String title, @Param("projects") List<BetInfoEntity> projects);
}
