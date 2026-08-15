package com.iot.bigdata.collector.entity;

import com.iot.bigdata.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据点表（时序采集数据）。按设备编码 + 指标 + 采集时间存储，面向聚合查询。
 *
 * <p>注意：数值字段使用 DECIMAL 而非 FLOAT/DOUBLE（阿里规约，避免精度丢失）；
 * 逻辑外键 {@code device_code} 仅存储编码，不建物理外键，并建联合索引支撑范围查询。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "device_data_point", indexes = {
        @Index(name = "idx_device_code_collect_time", columnList = "device_code, collect_time"),
        @Index(name = "idx_metric_collect_time", columnList = "metric, collect_time")
})
public class DeviceDataPoint extends BaseEntity {

    /** 设备编码（逻辑外键，对应 device.device_code）。 */
    @Column(name = "device_code", nullable = false, length = 64)
    private String deviceCode;

    /** 指标类型：temperature / humidity / power 等。 */
    @Column(name = "metric", nullable = false, length = 32)
    private String metric;

    /** 指标数值（DECIMAL 精确存储）。 */
    @Column(name = "metric_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal metricValue;

    /** 单位。 */
    @Column(name = "unit", length = 16)
    private String unit;

    /** 采集时间（用于趋势查询与分桶）。 */
    @Column(name = "collect_time", nullable = false)
    private LocalDateTime collectTime;

    /** 设备端上报时间戳（毫秒）。 */
    @Column(name = "device_ts")
    private Long deviceTs;
}
