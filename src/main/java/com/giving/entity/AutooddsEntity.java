package com.giving.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @TableName autoodds_info
 */
@Data
@TableName(value = "autoodds_info")
public class AutooddsEntity implements Serializable {

    @TableId
    private Long autooddsId;

    private Integer lotteryId;

    private Integer methodId;

    private String codeName;

    private BigDecimal defaultodds;

    private BigDecimal lowestodds;

    private BigDecimal highestodds;

    private String stage;

    private String trays;

    private Integer isCountoddsStage;

    private Integer isCountoddsTrays;

    private Integer isDeleteoddsStage;

    private Integer isDeleteoddsTrays;

    private BigDecimal blockadevalue;

    private BigDecimal broadenvalue;

    private Integer stopbet;

    private static final long serialVersionUID = 1L;
}
