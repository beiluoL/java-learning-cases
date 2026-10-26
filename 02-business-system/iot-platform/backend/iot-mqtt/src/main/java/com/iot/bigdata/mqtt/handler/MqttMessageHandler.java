package com.iot.bigdata.mqtt.handler;

/**
 * MQTT 消息处理器接口。各业务模块可实现该接口并注册为 Spring Bean，
 * 由 {@code MqttClientManager} 在消息到达时按 {@link #supports(String)} 分发。
 */
public interface MqttMessageHandler {

    /**
     * 判断是否处理该主题。
     *
     * @param topic MQTT 主题
     * @return 是否支持
     */
    boolean supports(String topic);

    /**
     * 处理消息。
     *
     * @param topic   MQTT 主题
     * @param payload 消息载荷（字符串）
     */
    void handle(String topic, String payload);
}
