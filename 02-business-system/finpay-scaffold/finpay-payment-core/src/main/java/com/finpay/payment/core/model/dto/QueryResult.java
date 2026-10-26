package com.finpay.payment.core.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 统一订单查询返回。 */
@Data
public class QueryResult {

    /** 商户订单号 */
    private String outTradeNo;
    /** 渠道交易号 */
    private String tradeNo;
    /** 金额（元） */
    private BigDecimal amount;
    /** 订单状态（映射到 PayStatus） */
    private String status;
    /** 支付成功时间（yyyy-MM-dd HH:mm:ss） */
    private String payTime;
    /** 渠道原始状态描述 */
    private String rawStatus;
}
