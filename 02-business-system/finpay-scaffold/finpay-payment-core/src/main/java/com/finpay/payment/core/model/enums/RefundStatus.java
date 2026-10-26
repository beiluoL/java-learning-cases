package com.finpay.payment.core.model.enums;

/** 退款状态枚举。 */
public enum RefundStatus {

    INIT("初始化"),
    PROCESSING("退款处理中"),
    SUCCESS("退款成功"),
    FAILED("退款失败");

    private final String label;

    RefundStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
