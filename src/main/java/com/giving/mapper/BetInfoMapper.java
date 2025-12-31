package com.giving.mapper;

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
		+ "select tt from ${table} "
		+ "where code = #{param}"
		+ "</script>"
	})
	/**
	 * 动态SQL示例（返回对象时，对象里的属性需要和查询字段一一对应，如果对应不上可以给查询出的字段取别名）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午8:37:45 
	* @param table 表名
	* @param param 查询参数
	 */
	public void test(@Param("table")String table,@Param("where1")String param);
}
