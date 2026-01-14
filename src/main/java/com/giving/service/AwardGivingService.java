package com.giving.service;

import com.giving.req.NoticeReq;

/** 
* @author yangxy
* @version 创建时间：2025年12月30日 下午5:03:49 
*/
public interface AwardGivingService {
	/**
	 * 通知派奖
	* @author yangxy
	* @version 创建时间：2025年12月30日 下午5:09:24 
	* @param noticeReq
	 */
    void notice(NoticeReq noticeReq);

	void createData();
}
