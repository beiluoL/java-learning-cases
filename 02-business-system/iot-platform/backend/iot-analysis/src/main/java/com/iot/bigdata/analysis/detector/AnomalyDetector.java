package com.iot.bigdata.analysis.detector;

import com.iot.bigdata.analysis.model.AlertLevel;
import com.iot.bigdata.analysis.rule.DefaultRuleProvider;
import com.iot.bigdata.analysis.rule.MetricRule;
import org.springframework.stereotype.Component;

/**
 * 异常检测器。基于阈值规则判定当前值是否越界，并返回告警级别。
 *
 * <p>超出正常区间越界幅度超过区间宽度 20% 判定为严重（CRITICAL），否则为警告（WARN）。</p>
 */
@Component
public class AnomalyDetector {

    private final DefaultRuleProvider ruleProvider;

    public AnomalyDetector(DefaultRuleProvider ruleProvider) {
        this.ruleProvider = ruleProvider;
    }

    /**
     * 检测指标值是否异常。
     *
     * @param metric 指标类型
     * @param value  当前值
     * @return 异常级别；未配置规则或处于正常范围时返回 null
     */
    public AlertLevel detect(String metric, double value) {
        MetricRule rule = ruleProvider.getRule(metric);
        if (rule == null || rule.isInRange(value)) {
            return null;
        }
        double range = rule.getMax() - rule.getMin();
        double breach = value < rule.getMin() ? rule.getMin() - value : value - rule.getMax();
        if (range > 0 && breach > 0.2 * range) {
            return AlertLevel.CRITICAL;
        }
        return AlertLevel.WARN;
    }
}
