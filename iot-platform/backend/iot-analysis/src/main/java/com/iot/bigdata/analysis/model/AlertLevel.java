package com.iot.bigdata.analysis.model;

/**
 * 告警级别。
 */
public enum AlertLevel {

    /** 提示。 */
    INFO("INFO", "提示"),
    /** 警告。 */
    WARN("WARN", "警告"),
    /** 严重。 */
    CRITICAL("CRITICAL", "严重");

    private final String code;
    private final String label;

    AlertLevel(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
