package com.iot.bigdata.collector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 设备新增/编辑请求。
 */
@Data
public class DeviceSaveRequest {

    /** 设备编码（业务唯一标识）。 */
    @NotBlank(message = "设备编码不能为空")
    @Size(max = 64, message = "设备编码长度不能超过64")
    private String deviceCode;

    /** 设备名称。 */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 128, message = "设备名称长度不能超过128")
    private String deviceName;

    /** 设备类型。 */
    @Size(max = 32, message = "设备类型长度不能超过32")
    private String deviceType;

    /** 通信协议。 */
    @Size(max = 16, message = "协议长度不能超过16")
    private String protocol;

    /** 安装位置。 */
    @Size(max = 128, message = "位置长度不能超过128")
    private String location;
}
