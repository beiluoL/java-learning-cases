package com.iot.bigdata.collector.entity;

import com.iot.bigdata.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 设备档案表（持久化设备清单，对应“已纳管设备”）。
 *
 * <p>遵循阿里建表规约：表名小写、必备 id/create_time/update_time/deleted 三/四字段、
 * 逻辑外键（此处无跨表关联）、唯一索引约束设备编码。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "device", indexes = {
        @Index(name = "uk_device_code", columnList = "device_code", unique = true),
        @Index(name = "idx_status", columnList = "status")
})
public class Device extends BaseEntity {

    /** 设备编码（业务唯一标识，对应 MQTT topic 中的 deviceId）。 */
    @Column(name = "device_code", nullable = false, length = 64)
    private String deviceCode;

    /** 设备名称。 */
    @Column(name = "device_name", length = 128)
    private String deviceName;

    /** 设备类型：sensor / gateway / controller 等。 */
    @Column(name = "device_type", length = 32)
    private String deviceType;

    /** 通信协议：MQTT 等。 */
    @Column(name = "protocol", length = 16)
    private String protocol;

    /** 安装位置。 */
    @Column(name = "location", length = 128)
    private String location;

    /** 状态：ONLINE / OFFLINE。 */
    @Column(name = "status", length = 16)
    private String status;

    /** 最近一次采集时间。 */
    @Column(name = "last_collect_time")
    private LocalDateTime lastCollectTime;
}
