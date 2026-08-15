package com.iot.bigdata.web.scheduler;

import com.iot.bigdata.analysis.rule.DefaultRuleProvider;
import com.iot.bigdata.analysis.rule.MetricRule;
import com.iot.bigdata.mqtt.gateway.MqttGateway;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import com.iot.bigdata.opinion.simulator.OpinionSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示数据调度器。周期性通过 MQTT 发布设备遥测（走完整链路：Broker→订阅→采集→分析→推送），
 * 并定期生成模拟舆情，使看板开箱即有实时数据。生产环境移除本类即可。
 */
@Component
public class DemoDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoDataScheduler.class);

    private final MqttGateway mqttGateway;
    private final OpinionSimulator opinionSimulator;
    private final DefaultRuleProvider ruleProvider;
    private final Random random = new Random();

    private static final List<String> DEVICES =
            List.of("dev-001", "dev-002", "dev-003", "dev-004", "dev-005");

    /** 指标基础配置：[标称最小值, 标称最大值, 步长]。常规随机游走落在此区间。 */
    private static final Map<String, double[]> METRIC_RANGE = new ConcurrentHashMap<>();
    private static final Map<String, String> METRIC_UNIT = new ConcurrentHashMap<>();

    static {
        METRIC_RANGE.put("temperature", new double[]{15.0, 35.0, 1.0});
        METRIC_RANGE.put("humidity", new double[]{30.0, 70.0, 2.0});
        METRIC_RANGE.put("power", new double[]{100.0, 800.0, 30.0});
        METRIC_RANGE.put("voltage", new double[]{210.0, 235.0, 2.0});
        METRIC_RANGE.put("co2", new double[]{400.0, 1200.0, 50.0});
        METRIC_UNIT.put("temperature", "°C");
        METRIC_UNIT.put("humidity", "%");
        METRIC_UNIT.put("power", "W");
        METRIC_UNIT.put("voltage", "V");
        METRIC_UNIT.put("co2", "ppm");
    }

    private static final List<String> METRICS =
            Arrays.asList("temperature", "humidity", "power", "voltage", "co2");

    /** 各设备各指标的当前值（随机游走状态）。 */
    private final Map<String, Double> state = new ConcurrentHashMap<>();

    public DemoDataScheduler(MqttGateway mqttGateway, OpinionSimulator opinionSimulator,
                             DefaultRuleProvider ruleProvider) {
        this.mqttGateway = mqttGateway;
        this.opinionSimulator = opinionSimulator;
        this.ruleProvider = ruleProvider;
        for (String dev : DEVICES) {
            for (String metric : METRICS) {
                double[] range = METRIC_RANGE.get(metric);
                state.put(key(dev, metric), (range[0] + range[1]) / 2);
            }
        }
    }

    /** 每 4 秒推送一批设备遥测。 */
    @Scheduled(fixedDelay = 4000)
    public void pushDeviceData() {
        for (String dev : DEVICES) {
            for (String metric : METRICS) {
                double value = nextValue(dev, metric);
                DeviceDataMessage msg = new DeviceDataMessage();
                msg.setDeviceId(dev);
                msg.setMetric(metric);
                msg.setValue(Math.round(value * 100.0) / 100.0);
                msg.setUnit(METRIC_UNIT.get(metric));
                msg.setTs(System.currentTimeMillis());
                mqttGateway.publishDeviceData(msg);
            }
        }
    }

    /** 每 15 秒生成一批模拟舆情。 */
    @Scheduled(fixedDelay = 15000)
    public void pushOpinion() {
        opinionSimulator.generate(3);
    }

    private double nextValue(String dev, String metric) {
        String k = key(dev, metric);
        double[] range = METRIC_RANGE.get(metric); // [标称min, 标称max, 步长]
        double step = range[2];
        double current = state.get(k);
        double next = current + (random.nextDouble() - 0.5) * 2 * step;
        // 标称区间边界反射，保证常规数据落在合理工作区间内（不为负、不过载）
        if (next < range[0]) {
            next = range[0] + (range[0] - next);
        }
        if (next > range[1]) {
            next = range[1] - (next - range[1]);
        }
        next = Math.max(range[0], Math.min(range[1], next));

        // 6% 概率制造越界尖峰，用于演示异常检测与实时告警：
        // 尖峰直接落在「规则阈值」之外（轻微越界 2%~20%），且保证物理合理——
        // 规则下界 > 0 的指标（功率/电压/CO2 等）只向上越界，避免产生负值。
        if (random.nextDouble() < 0.06) {
            MetricRule rule = ruleProvider.getRule(metric);
            if (rule != null) {
                double band = rule.getMax() - rule.getMin();
                double breach = band * (0.02 + random.nextDouble() * 0.18);
                boolean goLow = random.nextBoolean();
                if (goLow && rule.getMin() > 0) {
                    goLow = false;
                }
                next = goLow ? (rule.getMin() - breach) : (rule.getMax() + breach);
            }
        }
        state.put(k, next);
        return next;
    }

    private String key(String dev, String metric) {
        return dev + "|" + metric;
    }
}
