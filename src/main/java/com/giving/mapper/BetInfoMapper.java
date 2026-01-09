package com.giving.mapper;

import java.util.List;

import com.giving.req.NoticeReq;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.BetInfoEntity;

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

	void updateWinbonus(@Param("noticeReq") NoticeReq noticeReq, @Param("sumList") List<BetInfoEntity> sumList);

	void updateByNotWinList(@Param("noticeReq") NoticeReq noticeReq,@Param("notWinList") List<BetInfoEntity> notWinList);
}
