package com.iot.bigdata.collector.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据点视图对象。
 */
@Data
public class DeviceDataPointVO {

    /** 设备编码。 */
    private String deviceCode;

    /** 指标类型。 */
    private String metric;

    /** 指标数值。 */
    private BigDecimal value;

    /** 单位。 */
    private String unit;

    /** 采集时间。 */
    private LocalDateTime collectTime;
}
