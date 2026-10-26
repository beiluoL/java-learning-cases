package com.finpay.payment.core.model.enums;

/**
 * 支付渠道枚举。新增渠道只需实现 {@code PaymentChannel} 并向 {@code ChannelManager} 注册。
 */
public enum PayChannel {

    ALIPAY("alipay", "支付宝"),
    WECHAT("wechat", "微信支付"),
    UNIONPAY("unionpay", "银联");

    private final String code;
    private final String label;

    PayChannel(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }

    public static PayChannel of(String code) {
        for (PayChannel c : values()) {
            if (c.code.equalsIgnoreCase(code)) return c;
        }
        return null;
    }
}
