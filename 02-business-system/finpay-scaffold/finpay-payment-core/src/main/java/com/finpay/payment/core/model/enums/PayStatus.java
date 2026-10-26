package com.finpay.payment.core.model.enums;

/**
 * 支付订单状态机。核心流转：
 * INIT → PAYING → SUCCESS / FAILED / CLOSED
 * SUCCESS → REFUNDING → REFUNDED / REFUND_FAILED
 */
public enum PayStatus {

    INIT("初始化"),
    PAYING("支付中"),
    SUCCESS("支付成功"),
    FAILED("支付失败"),
    CLOSED("已关闭"),
    REFUNDING("退款中"),
    REFUNDED("已退款"),
    REFUND_FAILED("退款失败");

    private final String label;

    PayStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CLOSED || this == REFUNDED || this == REFUND_FAILED;
    }
}
