package com.finpay.payment.core.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 统一退款请求。 */
@Data
public class RefundRequest {

    /** 原商户订单号 */
    private String outTradeNo;

    /** 渠道侧原交易号（部分渠道必填） */
    private String tradeNo;

    /** 商户退款单号（幂等键） */
    private String outRefundNo;

    /** 退款金额（元） */
    private BigDecimal refundAmount;

    /** 原订单总金额（元，部分渠道用于校验） */
    private BigDecimal totalAmount;

    /** 退款原因 */
    private String reason;

    /** 渠道编码 */
    private String channel;

    /** 商户号 */
    private String merchantNo;
}
