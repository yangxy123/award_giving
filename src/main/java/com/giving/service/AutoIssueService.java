package com.giving.service;

import java.util.Date;
import java.util.List;

public interface AutoIssueService {

    /**
     * 自动生成奖期。
     *
     * <p>根据彩种配置生成未来奖期：普通彩种使用 endDate 作为结束日期，
     * 越南彩种使用 vietnamEndDate 作为结束日期，createNum 预留给按期数生成的彩种。</p>
     */
    void autoIssue(Date startDate, Date endDate, Date vietnamEndDate, Integer createNum);

}
