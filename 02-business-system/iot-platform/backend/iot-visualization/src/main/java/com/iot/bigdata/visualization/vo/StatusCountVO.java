package com.iot.bigdata.visualization.vo;

import lombok.Data;

/**
 * 状态计数（用于饼图/环形图）。
 */
@Data
public class StatusCountVO {

    /** 状态。 */
    private String status;

    /** 数量。 */
    private long count;
}
