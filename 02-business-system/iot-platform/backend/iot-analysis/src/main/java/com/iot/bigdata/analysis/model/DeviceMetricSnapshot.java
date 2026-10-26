package com.iot.bigdata.analysis.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备指标实时快照（窗口内聚合 + 当前值），用于看板实时展示。
 */
@Data
public class DeviceMetricSnapshot implements Serializable {

    /** 设备编码。 */
    private String deviceCode;

    /** 指标类型。 */
    private String metric;

    /** 单位。 */
    private String unit;

    /** 当前值。 */
    private BigDecimal currentValue;

    /** 窗口均值。 */
    private BigDecimal avg;

    /** 窗口最小值。 */
    private BigDecimal min;

    /** 窗口最大值。 */
    private BigDecimal max;

    /** 窗口采样数。 */
    private long count;

    /** 健康状态：NORMAL / WARNING / ABNORMAL。 */
    private String status;

    /** 最近更新时间。 */
    private LocalDateTime lastUpdate;
}
