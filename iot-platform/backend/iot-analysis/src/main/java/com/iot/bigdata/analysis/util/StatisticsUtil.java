package com.iot.bigdata.analysis.util;

import java.util.Collection;

/**
 * 统计工具。提供均值、标准差、极值等基础计算（基于 double）。
 */
public final class StatisticsUtil {

    private StatisticsUtil() {
    }

    public static double mean(Collection<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    public static double std(Collection<Double> values, double mean) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (Double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (values.size() - 1));
    }

    public static double min(Collection<Double> values) {
        double min = Double.MAX_VALUE;
        for (Double v : values) {
            if (v < min) {
                min = v;
            }
        }
        return values.isEmpty() ? 0.0 : min;
    }

    public static double max(Collection<Double> values) {
        double max = -Double.MAX_VALUE;
        for (Double v : values) {
            if (v > max) {
                max = v;
            }
        }
        return values.isEmpty() ? 0.0 : max;
    }
}
