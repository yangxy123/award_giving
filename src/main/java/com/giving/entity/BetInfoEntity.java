package com.giving.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * @author yangxy
 * @version 创建时间：2025年11月25日 上午11:43:37
 */
@Data
@TableName("TEMP_projects")
public class BetInfoEntity {
	@TableId
	private String projectId;// 方案ID
	private String userId;// 用户
	private String packageId;// 订单ID
	private String taskId;// 追号ID
	private Integer lotteryId;// 彩种ID
	private Integer methodId;// 玩法ID
	private String issue;// 奖期期号
	private Double bonus;// 实际派发的奖金
	private String winbonus;// 单注赢的钱
	private String code;// 号码
	private String codeType;// 号码投注方式[input:输入型,digital:数字型,dxds:大小单双]
	private Double singlePrice;// 单倍价格
	private String multiple;// 倍数
	private Double totalPrice;// 总共价格
	private Date writeTime;// 方案生成时间
	private String scode;//
	private String updateTime;// 方案更新时间
	private Date deductTime;// 真实扣款时间
	private Date bonusTime;// 奖金派发时间
	private Date cancelTime;// 撤单时间
	private Integer isDeduct;// 否是已经将投注的冻结金额转换成真实扣款 0=未(默认), 1=已扣
	private Integer isCancel;// 是否撤单(0:未撤单;1:自己撤单;2:公司撤单;其他以后再加)
	private Integer isGetprize;// 中奖状态(0:未判断;1:中奖;2:未中奖)
	private Integer prizeStatus;// 派奖状态(0:未派;1:已派)
	private Integer gameCancelCount;// 系统撤单次数
	private String userIp;//
	private String modes;// 模式id
	private String hashvar;//
	private String userPoint;// 用户返点
	private String isNew;// 是否特别奖金组 1是 0否
	private String comefrom;//
	private Integer pointStatus;// 返点状态 1已返点 0未返点
	private String thirdPartyTrxId;// 第三方ID
	private String platform;//
	private String log;//
	private String writeMicrotime;//
	private Date createdAt;//
	private Date updatedAt;//
	private String pointinfo;//

}
