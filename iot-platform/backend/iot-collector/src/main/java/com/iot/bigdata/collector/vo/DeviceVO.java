package com.iot.bigdata.collector.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备视图对象（接口返回）。字段命名采用小驼峰，由 JSON 序列化呈现。
 */
@Data
public class DeviceVO {

    /** 主键。 */
    private Long id;

    /** 设备编码。 */
    private String deviceCode;

    /** 设备名称。 */
    private String deviceName;

    /** 设备类型。 */
    private String deviceType;

    /** 通信协议。 */
    private String protocol;

    /** 安装位置。 */
    private String location;

    /** 状态。 */
    private String status;

    /** 最近采集时间。 */
    private LocalDateTime lastCollectTime;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
