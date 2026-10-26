package com.iot.bigdata.web.controller;

import com.iot.bigdata.common.result.ApiResult;
import com.iot.bigdata.mqtt.gateway.MqttGateway;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import com.iot.bigdata.opinion.simulator.OpinionSimulator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 模拟器接口。支持手动触发设备遥测与舆情生成，便于联调与演示。
 */
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {

    private static final List<String> DEVICES =
            Arrays.asList("dev-001", "dev-002", "dev-003", "dev-004", "dev-005");
    private static final List<String> METRICS =
            Arrays.asList("temperature", "humidity", "power", "voltage", "co2");

    private final MqttGateway mqttGateway;
    private final OpinionSimulator opinionSimulator;
    private final Random random = new Random();

    public SimulatorController(MqttGateway mqttGateway, OpinionSimulator opinionSimulator) {
        this.mqttGateway = mqttGateway;
        this.opinionSimulator = opinionSimulator;
    }

    @PostMapping("/device-data")
    public ApiResult<?> deviceData(@RequestParam(defaultValue = "5") int count,
                                   @RequestParam(required = false) String deviceId,
                                   @RequestParam(required = false) String metric) {
        for (int i = 0; i < count; i++) {
            String dev = deviceId != null && !deviceId.isEmpty()
                    ? deviceId : DEVICES.get(random.nextInt(DEVICES.size()));
            String m = metric != null && !metric.isEmpty()
                    ? metric : METRICS.get(random.nextInt(METRICS.size()));
            DeviceDataMessage msg = new DeviceDataMessage();
            msg.setDeviceId(dev);
            msg.setMetric(m);
            msg.setValue(Math.round((10 + random.nextDouble() * 80) * 100.0) / 100.0);
            msg.setUnit("sim");
            msg.setTs(System.currentTimeMillis());
            mqttGateway.publishDeviceData(msg);
        }
        return ApiResult.success("已生成设备数据 " + count + " 条");
    }

    @PostMapping("/opinion")
    public ApiResult<?> opinion(@RequestParam(defaultValue = "5") int count) {
        int n = opinionSimulator.generate(count);
        return ApiResult.success("已生成舆情 " + n + " 条");
    }
}
