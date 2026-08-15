package com.iot.bigdata.collector.service;

import com.iot.bigdata.collector.entity.Device;
import com.iot.bigdata.collector.entity.DeviceDataPoint;
import com.iot.bigdata.collector.repository.DeviceDataPointRepository;
import com.iot.bigdata.collector.repository.DeviceRepository;
import com.iot.bigdata.mqtt.event.DeviceDataReceivedEvent;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据采集服务。消费 {@link DeviceDataReceivedEvent}，完成设备自动纳管与数据点持久化。
 */
@Slf4j
@Service
public class DeviceDataService {

    private final DeviceRepository deviceRepository;
    private final DeviceDataPointRepository dataPointRepository;
    private final DeviceService deviceService;

    public DeviceDataService(DeviceRepository deviceRepository,
                             DeviceDataPointRepository dataPointRepository,
                             DeviceService deviceService) {
        this.deviceRepository = deviceRepository;
        this.dataPointRepository = dataPointRepository;
        this.deviceService = deviceService;
    }

    /**
     * 监听设备数据到达事件，落库并维护设备在线状态。
     */
    @EventListener
    @Transactional
    public void onDeviceData(DeviceDataReceivedEvent event) {
        DeviceDataMessage message = event.getMessage();
        ingest(message);
    }

    /**
     * 外部直接写入数据点（如 REST 模拟接口、测试工具）。
     */
    @Transactional
    public void ingest(DeviceDataMessage message) {
        String deviceCode = message.getDeviceId();
        Device device = deviceRepository.findByDeviceCode(deviceCode).orElse(null);
        if (device == null) {
            device = new Device();
            device.setDeviceCode(deviceCode);
            device.setDeviceName(deviceCode);
            device.setDeviceType("sensor");
            device.setProtocol("MQTT");
            device.setStatus("OFFLINE");
            log.info("[Collector] 自动纳管新设备: {}", deviceCode);
        }
        device.setStatus("ONLINE");
        device.setLastCollectTime(LocalDateTime.now());
        deviceRepository.save(device);

        DeviceDataPoint point = new DeviceDataPoint();
        point.setDeviceCode(deviceCode);
        point.setMetric(message.getMetric());
        point.setMetricValue(BigDecimal.valueOf(message.getValue()));
        point.setUnit(message.getUnit());
        point.setCollectTime(LocalDateTime.now());
        point.setDeviceTs(message.getTs() == 0 ? System.currentTimeMillis() : message.getTs());
        dataPointRepository.save(point);
    }
}
