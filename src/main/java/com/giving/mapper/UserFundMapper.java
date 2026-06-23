package com.giving.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.giving.entity.UserFundEntity;

/**
 * @author zzby
 * @description 针对表【TEMP_user_fund(用户钱包)】的数据库操作Mapper
 * @createDate 2026-01-09 16:57:33
 * @Entity com.giving.entity.TempUserFund
 */
public interface UserFundMapper extends BaseMapper<UserFundEntity> {

	/**
	 * 修改已经锁定的钱包金额
	 * 
	 * @param updateFund
	 * @param title
	 * @return
	 */
	int updateAddOrdersList(@Param("updateFund") UserFundEntity updateFund, @Param("title") String title);

	/**
	 * 通过type 与锁定状态取得一条
	 * 
	 * @param title
	 * @param userFund
	 * @return
	 */
	UserFundEntity selectByUserAndTypeOne(@Param("title") String title, @Param("userFund") UserFundEntity userFund);

	/**
	 * 执行锁定用户钱包
	 * 
	 * @param title
	 * @param userFund
	 * @return
	 */
	int updateLockedById(@Param("title") String title, @Param("userFund") UserFundEntity userFund,
			@Param("nowIsLock") Integer nowIsLock);

	@Update("update ${title}_user_fund SET islocked = #{islocked}," 
			+ "            lock_action = #{lockAction},"
			+ "            updated_at = now()" + "  WHERE userid = #{userid}" 
			+ "          AND islocked = #{nowIsLock}"
			+ " AND wallet_type = #{walletType}")
	/**
	 * 修改钱包状态
	 * @param userid 用户ID
	 * @param title 表头
	 * @param lockAction 操作说明
	 * @param islocked 是否上锁 0=正常, 1=被锁
	 * @param nowIsLock 当前锁定状态
	 * @param walletType 钱包类别 0: 充提, 1: 投注, 2: 验派, 3: 撤单, 4: 扣款 ,5 派奖
	 * @return
	 */
	int updateWalletLocked(@Param("userid") String userid, @Param("title") String title,
			@Param("lockAction") String lockAction, @Param("islocked") int islocked, 
			@Param("nowIsLock") int nowIsLock, @Param("walletType") int walletType);

	/**
	 * 取得用户全部钱包合集
	 * 
	 * @param title
	 * @param userId
	 * @return
	 */
	UserFundEntity selectByUserSum(@Param("title") String title, @Param("userId") String userId);

	/**
	 * 通过type 与锁定状态取得一条
	 *
	 * @param title
	 * @param userId
	 * @param walletType
	 * @return
	 */
	UserFundEntity selectByUserAndType(@Param("title") String title, @Param("userId") String userId,@Param("walletType") int walletType);

	/**
	 * 冻结投注金额
	 * @param title 厅主动态表前缀
	 * @param userId 用户ID
	 * @param walletType 钱包类型
	 * @param amount 冻结金额
	 * @return 更新行数
	 */
	int freezeBetAmount(@Param("title") String title, @Param("userId") String userId,
						@Param("walletType") int walletType, @Param("amount") java.math.BigDecimal amount);

	/**
	 * 更新已锁定的单个钱包余额
	 * @param title 厅主动态表前缀
	 * @param userFund 钱包
	 * @return 更新行数
	 */
	int updateLockedFund(@Param("title") String title, @Param("userFund") UserFundEntity userFund);

	/**
	 * 批量解锁--1
	 * 
	 * @param title
	 * @param userFundMap
	 * @param walletType
	 * @param lockAction
	 */
	void doLockUserFund(@Param("title") String title, @Param("userFundMap") Map<String, UserFundEntity> userFundMap,
			@Param("walletType") Integer walletType, @Param("lockAction") String lockAction);

	/**
	 * 批量修改钱包
	 * 
	 * @param title
	 * @param userFundMap
	 */
	int doUpdateAddOrdersList(@Param("title") String title,
			@Param("userFundMap") Map<String, UserFundEntity> userFundMap);

}
