package com.iot.bigdata.analysis.service;

import com.iot.bigdata.analysis.model.AnalysisAlert;
import com.iot.bigdata.analysis.model.DeviceMetricSnapshot;
import com.iot.bigdata.analysis.rule.DefaultRuleProvider;
import com.iot.bigdata.analysis.rule.MetricRule;
import com.iot.bigdata.analysis.store.AlertStore;
import com.iot.bigdata.analysis.store.RealtimeMetricsService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 实时分析门面。向接口层（iot-web）统一暴露实时指标、告警与阈值规则，
 * 屏蔽内部存储与检测细节，体现模块化边界。
 */
@Service
public class AnalysisFacade {

    private final RealtimeMetricsService realtimeMetricsService;
    private final AlertStore alertStore;
    private final DefaultRuleProvider ruleProvider;

    public AnalysisFacade(RealtimeMetricsService realtimeMetricsService,
                          AlertStore alertStore,
                          DefaultRuleProvider ruleProvider) {
        this.realtimeMetricsService = realtimeMetricsService;
        this.alertStore = alertStore;
        this.ruleProvider = ruleProvider;
    }

    public List<DeviceMetricSnapshot> realtimeSnapshots() {
        return realtimeMetricsService.getAll();
    }

    public List<DeviceMetricSnapshot> snapshotsByDevice(String deviceCode) {
        return realtimeMetricsService.getByDevice(deviceCode);
    }

    public List<AnalysisAlert> recentAlerts(int n) {
        return alertStore.recent(n);
    }

    public Map<String, MetricRule> rules() {
        return ruleProvider.allRules();
    }
}
