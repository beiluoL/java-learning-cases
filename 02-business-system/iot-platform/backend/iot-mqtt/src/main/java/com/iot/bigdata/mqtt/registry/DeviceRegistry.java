package com.iot.bigdata.mqtt.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备注册中心（内存态）。维护设备上下线状态与最近活跃时间，并定时扫描过期连接。
 *
 * <p>学习项目采用内存存储即可；生产环境应替换为 Redis 等共享存储以支持集群。</p>
 */
@Component
public class DeviceRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceRegistry.class);

    /** 状态：在线。 */
    public static final String STATUS_ONLINE = "ONLINE";

    /** 状态：离线。 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** 离线判定阈值（秒）：超过该时间未上报即视为离线。 */
    private static final int OFFLINE_TTL_SECONDS = 60;

    private final Map<String, DeviceConnection> registry = new ConcurrentHashMap<>();

    /**
     * 设备上报心跳。
     *
     * @param deviceId 设备编号
     * @param deviceName 设备名称（可为空，首次记录）
     * @param clientIp 来源 IP
     */
    public void heartbeat(String deviceId, String deviceName, String clientIp) {
        DeviceConnection conn = registry.computeIfAbsent(deviceId, k -> {
            DeviceConnection c = new DeviceConnection();
            c.setDeviceId(deviceId);
            c.setFirstSeenTime(LocalDateTime.now());
            c.setClientIp(clientIp);
            return c;
        });
        if (deviceName != null && !deviceName.isEmpty()) {
            conn.setDeviceName(deviceName);
        }
        if (clientIp != null && !clientIp.isEmpty()) {
            conn.setClientIp(clientIp);
        }
        conn.setLastActiveTime(LocalDateTime.now());
        conn.setStatus(STATUS_ONLINE);
    }

    public void offline(String deviceId) {
        DeviceConnection conn = registry.get(deviceId);
        if (conn != null) {
            conn.setStatus(STATUS_OFFLINE);
        }
    }

    public DeviceConnection get(String deviceId) {
        return registry.get(deviceId);
    }

    public List<DeviceConnection> listAll() {
        return new ArrayList<>(registry.values());
    }

    public long onlineCount() {
        return registry.values().stream()
                .filter(c -> STATUS_ONLINE.equals(c.getStatus()))
                .count();
    }

    /** 定时扫描：将超过阈值未活跃的设备标记为离线。 */
    @Scheduled(fixedDelay = 30000)
    public void scanExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_TTL_SECONDS);
        for (DeviceConnection conn : registry.values()) {
            if (STATUS_ONLINE.equals(conn.getStatus())
                    && conn.getLastActiveTime() != null
                    && conn.getLastActiveTime().isBefore(threshold)) {
                conn.setStatus(STATUS_OFFLINE);
                log.debug("[Registry] 设备 {} 超过 {}s 未活跃，标记为离线", conn.getDeviceId(), OFFLINE_TTL_SECONDS);
            }
        }
    }
}
