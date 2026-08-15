package com.iot.bigdata.analysis.rule;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 默认阈值规则提供方。学习项目内置常见指标的合理区间，可后续改为数据库/配置中心驱动。
 */
@Component
public class DefaultRuleProvider {

    private final Map<String, MetricRule> ruleMap = new HashMap<>();

    public DefaultRuleProvider() {
        ruleMap.put("temperature", new MetricRule("temperature", -20.0, 80.0, "°C"));
        ruleMap.put("humidity", new MetricRule("humidity", 0.0, 100.0, "%"));
        ruleMap.put("power", new MetricRule("power", 0.0, 5000.0, "W"));
        ruleMap.put("voltage", new MetricRule("voltage", 180.0, 260.0, "V"));
        ruleMap.put("pressure", new MetricRule("pressure", 0.0, 10.0, "kPa"));
        ruleMap.put("co2", new MetricRule("co2", 300.0, 5000.0, "ppm"));
    }

    public MetricRule getRule(String metric) {
        return ruleMap.get(metric);
    }

    public Map<String, MetricRule> allRules() {
        return new HashMap<>(ruleMap);
    }
}
