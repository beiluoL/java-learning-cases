package com.ecommerce.learn.module.pay.dto;

import lombok.Data;

/**
 * 支付回调通知（来自支付网关；此处为模拟结构）
 */
@Data
public class PayNotifyDTO {

    /** 支付渠道 */
    private String channel;
    /** 商户订单号 */
    private String orderNo;
    /** 第三方交易号 */
    private String tradeNo;
    /** 是否支付成功 */
    private boolean success;
    /** 实际支付金额（分/元，按渠道） */
    private java.math.BigDecimal amount;
}
