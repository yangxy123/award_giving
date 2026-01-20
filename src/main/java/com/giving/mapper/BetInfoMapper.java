package com.giving.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.entity.OrdersEntity;
import com.giving.entity.TempIssueInfoEntity;
import com.giving.entity.UserFundEntity;
import com.giving.req.NoticeReq;
import org.apache.catalina.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.BetInfoEntity;
import org.apache.ibatis.annotations.Update;

/** 
* @author yangxy
* @version 创建时间：2025年12月30日 下午5:10:22 
*/
public interface BetInfoMapper extends BaseMapper<BetInfoEntity> {


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

	List<BetInfoEntity> selectListByNoticeReq(@Param("noticeReq") NoticeReq noticeReq);

	Integer countListByNoticeReq(@Param("noticeReq") NoticeReq noticeReq);

	void updateWinbonus(@Param("noticeReq") NoticeReq noticeReq, @Param("sumList") List<BetInfoEntity> sumList,@Param("bonusTime") Date bonusTime);

	void updateByNotWinList(@Param("noticeReq") NoticeReq noticeReq,@Param("notWinList") List<BetInfoEntity> notWinList);

	void doLockUserFund(@Param("title") String title,@Param("userIds") List<String> userIds,@Param("walletType") Integer walletType);

	//void addOrdersReArray(@Param("noticeReq") NoticeReq noticeReq,@Param("sumList") List<BetInfoEntity> sumList);

	void unLockUserFund(@Param("noticeReq") NoticeReq noticeReq,@Param("userIds") List<String> userIds);

	List<UserFundEntity> selectUserFundBalancesForUpdate(@Param("noticeReq") NoticeReq noticeReq, @Param("userIds") List<String> userIds);

	int batchInsertOrders(@Param("noticeReq") NoticeReq noticeReq, @Param("orders") List<OrdersEntity> orders);

	/**
	 * 获取所有尚未'真实扣款'的方案
	 * @return
	 */
	List<BetInfoEntity> checkProjects(@Param("title") String title, @Param("issue") TempIssueInfoEntity issue);

	/**
	 * 修改为已扣款
	 * @param updateProject
	 * @return
	 */
    void updateDeduct(@Param("project") BetInfoEntity updateProject,@Param("title") String title);
}
