package com.giving.entity;

import lombok.Data;

@Data
public class IssueIdProducerEntity {
    /** issue_id_producer 自动生成的全局奖期 ID。 */
    private Long issueId;

    /** 奖期分表后缀；0 表示主表 issue_info。 */
    private String suffix;
}
