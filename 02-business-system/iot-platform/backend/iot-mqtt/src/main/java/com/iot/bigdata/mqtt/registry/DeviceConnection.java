package com.iot.bigdata.mqtt.registry;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备连接状态快照（内存态，用于看板实时展示与上下线判定）。
 */
public class DeviceConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备编号。 */
    private String deviceId;

    /** 设备名称。 */
    private String deviceName;

    /** 状态：ONLINE / OFFLINE。 */
    private String status;

    /** 首次接入时间。 */
    private LocalDateTime firstSeenTime;

    /** 最近活跃时间。 */
    private LocalDateTime lastActiveTime;

    /** 来源 IP（可选）。 */
    private String clientIp;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getFirstSeenTime() {
        return firstSeenTime;
    }

    public void setFirstSeenTime(LocalDateTime firstSeenTime) {
        this.firstSeenTime = firstSeenTime;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
}
