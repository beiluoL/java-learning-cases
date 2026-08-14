package com.example.csagent.dto;

/** 意图类型（与 FAQ category 对齐）。 */
public enum Intent {
    REFUND("退款"),
    ORDER_QUERY("查询订单"),
    TECH_SUPPORT("技术支持"),
    UNKNOWN("未知");

    private final String label;

    Intent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Intent from(String s) {
        if (s == null) return UNKNOWN;
        for (Intent i : values()) {
            if (i.name().equalsIgnoreCase(s)) return i;
        }
        return UNKNOWN;
    }
}
