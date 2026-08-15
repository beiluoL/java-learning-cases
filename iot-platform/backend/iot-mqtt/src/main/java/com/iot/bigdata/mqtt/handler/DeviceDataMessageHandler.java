package com.iot.bigdata.mqtt.handler;

import com.iot.bigdata.mqtt.event.DeviceDataReceivedEvent;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import com.iot.bigdata.mqtt.registry.DeviceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 设备上行数据处理器。匹配 {@code iot/device/{deviceId}/data} 主题，
 * 解析后更新设备注册中心并发布 {@link DeviceDataReceivedEvent} 供下游消费。
 */
@Component
public class DeviceDataMessageHandler implements MqttMessageHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataMessageHandler.class);

    /** 设备数据主题前缀。 */
    private static final String TOPIC_PREFIX = "iot/device/";

    /** 数据主题后缀。 */
    private static final String TOPIC_SUFFIX = "/data";

    private final ApplicationEventPublisher eventPublisher;
    private final DeviceRegistry deviceRegistry;

    public DeviceDataMessageHandler(ApplicationEventPublisher eventPublisher, DeviceRegistry deviceRegistry) {
        this.eventPublisher = eventPublisher;
        this.deviceRegistry = deviceRegistry;
    }

    @Override
    public boolean supports(String topic) {
        return topic != null && topic.startsWith(TOPIC_PREFIX) && topic.endsWith(TOPIC_SUFFIX);
    }

    @Override
    public void handle(String topic, String payload) {
        String deviceId = extractDeviceId(topic);
        if (deviceId == null) {
            log.warn("[Handler] 无法从主题解析设备编号: {}", topic);
            return;
        }
        DeviceDataMessage message = DeviceDataMessage.fromJson(payload);
        if (message == null) {
            log.warn("[Handler] 设备 {} 数据解析失败, payload={}", deviceId, payload);
            return;
        }
        if (message.getDeviceId() == null || message.getDeviceId().isEmpty()) {
            message.setDeviceId(deviceId);
        }
        if (message.getTs() == 0) {
            message.setTs(System.currentTimeMillis());
        }
        deviceRegistry.heartbeat(deviceId, null, null);
        eventPublisher.publishEvent(new DeviceDataReceivedEvent(this, deviceId, message));
        log.debug("[Handler] 设备 {} 指标 {} = {} {}", deviceId, message.getMetric(), message.getValue(), message.getUnit());
    }

    private String extractDeviceId(String topic) {
        if (topic == null) {
            return null;
        }
        String trimmed = topic.substring(TOPIC_PREFIX.length());
        int end = trimmed.indexOf(TOPIC_SUFFIX);
        if (end <= 0) {
            return null;
        }
        return trimmed.substring(0, end);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
