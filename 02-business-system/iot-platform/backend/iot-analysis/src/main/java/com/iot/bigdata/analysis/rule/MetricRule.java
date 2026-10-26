package com.iot.bigdata.analysis.rule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 指标阈值规则。用于异常检测：超出 [min, max] 视为越界。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricRule implements Serializable {

    /** 指标类型。 */
    private String metric;

    /** 正常范围下界（含）。 */
    private double min;

    /** 正常范围上界（含）。 */
    private double max;

    /** 单位。 */
    private String unit;

    public boolean isInRange(double value) {
        return value >= min && value <= max;
    }
}
