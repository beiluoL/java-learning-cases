package com.iot.bigdata.analysis.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警记录（实时分析产生）。看板与接口读取本对象进行展示。
 */
@Data
public class AnalysisAlert implements Serializable {

    /** 告警编号。 */
    private String alertId;

    /** 设备编码。 */
    private String deviceCode;

    /** 指标类型。 */
    private String metric;

    /** 触发值。 */
    private double value;

    /** 阈值说明（如 "正常区间 [0.0, 100.0]"）。 */
    private String thresholdDesc;

    /** 告警级别。 */
    private AlertLevel level;

    /** 描述。 */
    private String message;

    /** 产生时间。 */
    private LocalDateTime time;
}
