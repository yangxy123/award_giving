package com.giving.service.autoissue;

import lombok.Data;

/**
 * issueset 配置解析后的时间段配置。
 *
 * <p>用于描述某个彩种每天的开奖区间、周期、停售时间、撤单截止时间等信息。</p>
 */
@Data
public class IssueSet {
    private String starttime;
    private String firstendtime;
    private String endtime;
    private int cycle;
    private int endsale;
    private int inputcodetime;
    private int droptime;
    private int status;
    private int sort;
    private boolean starttimePositive = true;
}
