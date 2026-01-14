package com.giving.service;

import com.giving.req.NoticeReq;

/** 
* @author yangxy
* @version 创建时间：2025年12月30日 下午5:03:49 
*/
public interface AwardGivingService {
	/**
	 * 通知派奖（越南彩18组）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午5:09:24 
	* @param noticeReq
	 */
	public void notice(NoticeReq noticeReq);
	
	/**
	 * 通知派奖（越南彩28组）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午5:09:24 
	* @param noticeReq
	 */
	public void noticeNorth(NoticeReq noticeReq);
	
	/**
	 * 通知派奖（泰国彩）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午5:09:24 
	* @param noticeReq
	 */
	public void noticeTh(NoticeReq noticeReq);

	void createData();
}
