package com.iot.bigdata.collector.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 指标聚合统计视图对象。
 */
@Data
public class MetricStatVO {

    /** 设备编码（平台级聚合时存在）。 */
    private String deviceCode;

    /** 指标类型。 */
    private String metric;

    /** 均值。 */
    private BigDecimal avg;

    /** 最小值。 */
    private BigDecimal min;

    /** 最大值。 */
    private BigDecimal max;

    /** 采样点数。 */
    private Long count;
}
