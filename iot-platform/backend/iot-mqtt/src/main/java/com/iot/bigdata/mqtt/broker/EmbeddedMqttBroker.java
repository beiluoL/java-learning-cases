package com.iot.bigdata.mqtt.broker;

import io.moquette.BrokerConstants;
import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;

/**
 * 内嵌 MQTT Broker（基于 moquette）。项目启动即在 1883 端口提供 MQTT 服务，
 * 使得整套系统开箱即用、无需额外部署Broker，便于学习与二次开发。
 *
 * <p>生产环境可将本类替换为连接外部 EMQX / Mosquitto 的配置。</p>
 */
@Component
public class EmbeddedMqttBroker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedMqttBroker.class);

    @Value("${iot.mqtt.port:1883}")
    private int port;

    private Server server;

    @PostConstruct
    public void start() {
        try {
            Properties props = new Properties();
            props.setProperty(BrokerConstants.PORT_PROPERTY_NAME, String.valueOf(port));
            props.setProperty(BrokerConstants.HOST_PROPERTY_NAME, "0.0.0.0");
            props.setProperty(BrokerConstants.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
            props.setProperty(BrokerConstants.BROKER_INTERCEPTOR_THREAD_POOL_SIZE, "10");
            props.setProperty(BrokerConstants.DATA_PATH_PROPERTY_NAME, "./data/mqtt");

            IConfig config = new MemoryConfig(props);
            server = new Server();
            server.startServer(config);
            waitUntilPortReady();
            log.info("[MQTT] 内嵌 Broker 已启动，监听端口: {}", port);
        } catch (Exception e) {
            log.error("[MQTT] 内嵌 Broker 启动失败", e);
            throw new IllegalStateException("内嵌 MQTT Broker 启动失败", e);
        }
    }

    /**
     * 等待端口真正就绪。moquette 的 {@code startServer} 采用异步绑定，
     * 若不等待直接返回，下游 MQTT 客户端可能在端口尚未 listen 时连接而失败。
     */
    private void waitUntilPortReady() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("localhost", port), 500);
                return;
            } catch (IOException e) {
                Thread.sleep(200L);
            }
        }
        log.warn("[MQTT] 端口 {} 在等待期内未确认就绪，客户端连接可能失败", port);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stopServer();
            log.info("[MQTT] 内嵌 Broker 已停止");
        }
    }
}
