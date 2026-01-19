package com.giving.service;

import com.giving.base.resp.ApiResp;
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

	/**
	 * 通知派奖（老挝彩、马来西亚）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午5:09:24
	* @param noticeReq
	 */
	public void noticeLw(NoticeReq noticeReq);

	/**
	 * 通知派奖（快三）
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午5:09:24
	* @param noticeReq
	 */
	public void noticeKs(NoticeReq noticeReq);

	ApiResp<String> createData(Integer count);
}
