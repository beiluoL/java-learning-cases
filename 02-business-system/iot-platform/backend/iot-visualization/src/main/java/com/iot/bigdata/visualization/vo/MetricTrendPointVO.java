package com.iot.bigdata.visualization.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 指标趋势点。供折线图直接使用。
 */
@Data
public class MetricTrendPointVO {

    /** 时间标签（HH:mm:ss）。 */
    private String time;

    /** 数值。 */
    private BigDecimal value;
}
