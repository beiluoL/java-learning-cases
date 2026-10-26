package com.iot.bigdata.mqtt.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 模块配置。绑定 {@link MqttProperties}，使其可被 {@code @Autowired} 注入。
 */
@Configuration
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfiguration {
}
