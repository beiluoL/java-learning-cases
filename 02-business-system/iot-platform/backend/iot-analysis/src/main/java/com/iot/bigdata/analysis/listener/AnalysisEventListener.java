package com.iot.bigdata.analysis.listener;

import com.iot.bigdata.analysis.detector.AnomalyDetector;
import com.iot.bigdata.analysis.event.AnalysisAlertEvent;
import com.iot.bigdata.analysis.model.AnalysisAlert;
import com.iot.bigdata.analysis.model.AlertLevel;
import com.iot.bigdata.analysis.rule.DefaultRuleProvider;
import com.iot.bigdata.analysis.rule.MetricRule;
import com.iot.bigdata.analysis.store.AlertStore;
import com.iot.bigdata.analysis.store.RealtimeMetricsService;
import com.iot.bigdata.common.util.IdGenerator;
import com.iot.bigdata.mqtt.event.DeviceDataReceivedEvent;
import com.iot.bigdata.mqtt.model.DeviceDataMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 实时分析监听器。消费设备数据事件：更新实时指标快照、执行异常检测，
 * 命中阈值时记录告警并发布 {@link AnalysisAlertEvent} 供 WebSocket 推送。
 */
@Slf4j
@Component
public class AnalysisEventListener {

    private final RealtimeMetricsService realtimeMetricsService;
    private final AnomalyDetector anomalyDetector;
    private final AlertStore alertStore;
    private final DefaultRuleProvider ruleProvider;
    private final ApplicationEventPublisher eventPublisher;

    public AnalysisEventListener(RealtimeMetricsService realtimeMetricsService,
                                 AnomalyDetector anomalyDetector,
                                 AlertStore alertStore,
                                 DefaultRuleProvider ruleProvider,
                                 ApplicationEventPublisher eventPublisher) {
        this.realtimeMetricsService = realtimeMetricsService;
        this.anomalyDetector = anomalyDetector;
        this.alertStore = alertStore;
        this.ruleProvider = ruleProvider;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void onDeviceData(DeviceDataReceivedEvent event) {
        DeviceDataMessage message = event.getMessage();
        String deviceCode = message.getDeviceId();
        String metric = message.getMetric();

        realtimeMetricsService.onData(deviceCode, metric, message.getValue(), message.getUnit());

        AlertLevel level = anomalyDetector.detect(metric, message.getValue());
        if (level == null) {
            return;
        }

        realtimeMetricsService.updateStatus(deviceCode, metric,
                level == AlertLevel.CRITICAL ? "ABNORMAL" : "WARNING");

        MetricRule rule = ruleProvider.getRule(metric);
        String thresholdDesc = rule == null ? "无阈值" : String.format("正常区间 [%s, %s]%s",
                rule.getMin(), rule.getMax(), rule.getUnit() == null ? "" : rule.getUnit());

        AnalysisAlert alert = new AnalysisAlert();
        alert.setAlertId(IdGenerator.nextIdStr());
        alert.setDeviceCode(deviceCode);
        alert.setMetric(metric);
        alert.setValue(message.getValue());
        alert.setThresholdDesc(thresholdDesc);
        alert.setLevel(level);
        alert.setMessage(String.format("设备 %s 的 %s 指标值 %s 超出正常范围", deviceCode, metric, message.getValue()));
        alert.setTime(LocalDateTime.now());

        alertStore.add(alert);
        eventPublisher.publishEvent(new AnalysisAlertEvent(this, alert));
        log.info("[Analysis] 产生告警 device={}, metric={}, level={}", deviceCode, metric, level);
    }
}
