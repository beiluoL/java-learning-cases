package com.iot.bigdata.mqtt.client;

import com.iot.bigdata.mqtt.config.MqttProperties;
import com.iot.bigdata.mqtt.handler.MqttMessageHandler;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * MQTT 客户端管理器。负责连接内嵌 Broker、订阅主题，并把到达的消息按处理器链分发。
 *
 * <p>既作为订阅端（接收设备上行数据），也作为发布端（下发命令、推送模拟数据），供 {@link com.iot.bigdata.mqtt.gateway.MqttGateway} 使用。</p>
 */
@Component
@DependsOn("embeddedMqttBroker")
public class MqttClientManager {

    private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);

    /** 订阅根主题：覆盖所有 iot 设备相关消息。 */
    private static final String SUBSCRIBE_ROOT = "iot/#";

    private final MqttProperties properties;
    private final List<MqttMessageHandler> handlers;

    private MqttClient client;

    public MqttClientManager(MqttProperties properties, List<MqttMessageHandler> handlers) {
        this.properties = properties;
        this.handlers = handlers;
        AnnotationAwareOrderComparator.sort(this.handlers);
    }

    @PostConstruct
    public void connect() {
        String serverUri = String.format("tcp://%s:%d", properties.getHost(), properties.getPort());
        String clientId = properties.getClientIdPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            client = new MqttClient(serverUri, clientId, new MemoryPersistence());
            client.setCallback(new InternalCallback());
            // 让同步 publish(QoS1) 真正阻塞等待 PUBACK，而非立即返回，避免 in-flight 计数堆积。
            client.setTimeToWait(5000L);
            doConnectWithRetry();
        } catch (MqttException e) {
            log.error("[MQTT] 客户端创建失败, serverUri={}", serverUri, e);
            throw new IllegalStateException("MQTT 客户端创建失败", e);
        }
    }

    private void doConnectWithRetry() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(properties.isAutomaticReconnect());
        options.setCleanSession(true);
        options.setKeepAliveInterval(properties.getKeepAlive());
        options.setConnectionTimeout(properties.getConnectionTimeout());
        // 指令/命令类消息使用 QoS 1，需放宽 in-flight 上限，避免批量/并发发布触发 MAX_INFLIGHT。
        options.setMaxInflight(200);
        if (properties.getUsername() != null && !properties.getUsername().isEmpty()) {
            options.setUserName(properties.getUsername());
            options.setPassword(properties.getPassword() == null ? new char[0] : properties.getPassword().toCharArray());
        }

        int maxRetry = 5;
        for (int i = 1; i <= maxRetry; i++) {
            try {
                client.connect(options);
                log.info("[MQTT] 客户端已连接 Broker: {}", client.getServerURI());
                subscribeRoot();
                return;
            } catch (MqttException e) {
                log.warn("[MQTT] 第 {}/{} 次连接失败: {}", i, maxRetry, e.getMessage());
                if (i == maxRetry) {
                    throw e;
                }
                try {
                    Thread.sleep(1000L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("MQTT 连接被中断", ie);
                }
            }
        }
    }

    private void subscribeRoot() throws MqttException {
        client.subscribe(SUBSCRIBE_ROOT, 1);
        log.info("[MQTT] 已订阅主题: {}", SUBSCRIBE_ROOT);
    }

    /**
     * 发布消息。
     *
     * @param topic   主题
     * @param payload 载荷
     * @param qos     QoS
     */
    public void publish(String topic, String payload, int qos) {
        if (client == null || !client.isConnected()) {
            log.warn("[MQTT] 客户端未连接，丢弃消息 topic={}", topic);
            return;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            client.publish(topic, message);
        } catch (MqttException e) {
            log.error("[MQTT] 发布消息失败 topic={}", topic, e);
        }
    }

    @PreDestroy
    public void disconnect() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
                log.info("[MQTT] 客户端已断开");
            } catch (MqttException e) {
                log.warn("[MQTT] 断开连接异常", e);
            }
        }
    }

    /** 内部回调：连接完成后重订阅，并把消息分发到处理器链。 */
    private class InternalCallback implements MqttCallbackExtended {

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("[MQTT] 连接丢失，等待自动重连...", cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            log.debug("[MQTT] 收到消息 topic={}, payload={}", topic, payload);
            for (MqttMessageHandler handler : handlers) {
                try {
                    if (handler.supports(topic)) {
                        handler.handle(topic, payload);
                    }
                } catch (Exception e) {
                    log.error("[MQTT] 处理器 {} 处理主题 {} 失败", handler.getClass().getSimpleName(), topic, e);
                }
            }
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // 交付完成回调（发布侧），本学习项目无需处理。
        }

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            log.info("[MQTT] 连接成功 (reconnect={}), serverUri={}", reconnect, serverUri);
            try {
                subscribeRoot();
            } catch (MqttException e) {
                log.error("[MQTT] 重订阅失败", e);
            }
        }
    }
}
