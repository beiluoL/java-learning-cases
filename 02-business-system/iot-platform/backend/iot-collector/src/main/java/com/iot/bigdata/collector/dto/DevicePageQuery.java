package com.iot.bigdata.collector.dto;

import lombok.Data;

/**
 * 设备分页查询条件。
 */
@Data
public class DevicePageQuery {

    /** 设备编码（模糊）。 */
    private String deviceCode;

    /** 设备类型。 */
    private String deviceType;

    /** 状态。 */
    private String status;

    /** 页码（从 1 开始）。 */
    private Long pageNum;

    /** 每页大小。 */
    private Long pageSize;
}
