package com.giving.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 
* @author yangxy
* @version 创建时间：2026年1月21日 下午3:00:39 
*/
@Repository
public class JdbcCreateSqlUtil {
	@Autowired
    private JdbcTemplate jdbcTemplate;
    
	/**
	 * 批量修改
	* @author yangxy
	* @version 创建时间：2026年1月21日 下午3:10:42 
	* @param list 带更新对象列表
	* @param sql (示例String sql = "UPDATE your_table SET column1 = ?, column2 = ? WHERE id = ?")
	* @param setColumns 修改字段对应实体属性列表
	* @param whereColumns 查询字段对应实体属性列表
	 */
	public <T> void batchUpdate(List<T> list, String sql, List<String> setColumns, List<String> whereColumns) {
		jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps, int i) throws SQLException {
				T entity = list.get(i);
				for (int j = 0; j < setColumns.size(); j++) {
					Object fieldValue = ReflectionUtils.getFieldValue(entity, setColumns.get(j));
					ps.setObject(j + 1, fieldValue);
				}

				int setColumnsSize = setColumns.size();
				for (int j = 0; j < whereColumns.size(); j++) {
					Object fieldValue = ReflectionUtils.getFieldValue(entity, whereColumns.get(j));
					ps.setObject(j + 1 + setColumnsSize, fieldValue);
				}
			}
			@Override
			public int getBatchSize() {
				return list.size();
			}
		});
	}
}
