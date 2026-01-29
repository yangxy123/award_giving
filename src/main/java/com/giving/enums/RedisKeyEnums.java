package com.giving.enums;

/**
 * redis缓存key枚举
 * @author yangxy
 * @version 创建时间：2023年10月26日 下午5:12:00
 */
public enum RedisKeyEnums {
	/**
	 * 亚洲30秒快三控号列表
	 */
	QUICK_THREE_THIRTYSECONDS("quickthree:asia:thirtySeconds:",2),
	/**
	 * 亚洲1分快三控号列表
	 */
	QUICK_THREE_ONEMINUTE("quickthree:asia:oneminute:",2),
	/**
	 * 澳洲5分快三控号列表
	 */
	QUICK_THREE_FIVEMINUTE("quickthree:australia:fiveminute:",2),
	/**
	 * 泰国彩其他控号列表
	 */
	TH_OTHER("th:other:",2),
	/**
	 * 泰国彩后三控号列表
	 */
	TH_AFTER_THREE("th:after:three:",2),
	/**
	 * 泰国彩前三控号列表
	 */
	TH_FONT_THREE("th:font:three:",2),
	/**
	 * 越南30秒彩控号列表
	 */
	VND_THRITYSECONDS("vnd:control:thirtySeconds:",2),
	/**
	 * 越南5分彩控号列表
	 */
	VND_FIVEMINUTES("vnd:control:fiveMinutes:",2),
	/**
	 * 开奖号码通知接口
	 */
	NOTICE_URL("bill:notice:url",0),
	/**
	 * 商户盈利率阈值
	 */
	MERCHANT_THRESHOLD("merchant:threshold",0),
	/**
	 * 商户当前盈利率
	 */
	MERCHANT_NOW_THRESHOLD("merchant:now:threshold:",0),
	/**
	 * 投注金派奖金每日统计
	 */
	C_PROFIT_DATA("c_profit_data",4);
	/**
	 * redisKey
	 */
	public String key;
	/**
	 * 数据类型类型（0 string;1 list;2 zset;3 set;4 hash）
	 */
	public Integer dateType;

	private RedisKeyEnums(String key, Integer dateType) {
		this.key = key;
		this.dateType = dateType;
	}
}
