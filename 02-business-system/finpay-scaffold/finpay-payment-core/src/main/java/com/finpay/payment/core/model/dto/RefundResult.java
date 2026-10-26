package com.finpay.payment.core.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 统一退款结果。 */
@Data
public class RefundResult {

    private String outRefundNo;
    private String refundTradeNo;
    private String status;
    private BigDecimal refundAmount;
}
