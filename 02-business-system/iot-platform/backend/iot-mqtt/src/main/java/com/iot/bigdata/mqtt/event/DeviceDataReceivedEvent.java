package com.iot.bigdata.mqtt.event;

import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import org.springframework.context.ApplicationEvent;

/**
 * 设备数据到达事件。MQTT 客户端收到上行数据并解析后发布，供采集/分析模块异步消费（解耦）。
 */
public class DeviceDataReceivedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 设备编号。 */
    private final String deviceId;

    /** 解析后的数据点。 */
    private final DeviceDataMessage message;

    public DeviceDataReceivedEvent(Object source, String deviceId, DeviceDataMessage message) {
        super(source);
        this.deviceId = deviceId;
        this.message = message;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public DeviceDataMessage getMessage() {
        return message;
    }
}
