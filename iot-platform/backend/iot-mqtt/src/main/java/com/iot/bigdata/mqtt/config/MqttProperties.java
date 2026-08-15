package com.iot.bigdata.mqtt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 客户端连接配置（绑定配置文件 {@code iot.mqtt.*}）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iot.mqtt")
public class MqttProperties {

    /** Broker 地址。 */
    private String host = "localhost";

    /** Broker 端口。 */
    private int port = 1883;

    /** 客户端 ID 前缀，用于区分不同后端实例。 */
    private String clientIdPrefix = "iot-platform-backend";

    /** 连接用户名（内嵌 Broker 默认允许匿名，可留空）。 */
    private String username;

    /** 连接密码。 */
    private String password;

    /** 心跳间隔（秒）。 */
    private int keepAlive = 30;

    /** 连接超时（秒）。 */
    private int connectionTimeout = 10;

    /** 是否自动重连。 */
    private boolean automaticReconnect = true;
}
