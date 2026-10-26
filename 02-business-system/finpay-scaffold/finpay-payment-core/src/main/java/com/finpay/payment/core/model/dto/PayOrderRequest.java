package com.finpay.payment.core.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 统一下单请求。业务层只关心「收多少钱、什么渠道、谁付、成功后跳哪」，不感知渠道差异。
 */
@Data
public class PayOrderRequest {

    /** 商户订单号（幂等键，由业务层用雪花算法生成） */
    private String outTradeNo;

    /** 渠道编码：alipay / wechat */
    private String channel;

    /** 商户号（对应渠道侧配置） */
    private String merchantNo;

    /** 金额（元，BigDecimal 避免浮点精度问题） */
    private BigDecimal amount;

    /** 币种，默认 CNY */
    private String currency = "CNY";

    /** 商品标题 */
    private String subject;

    /** 商品描述 */
    private String body;

    /** 支付结果异步回调地址（渠道回调我方） */
    private String notifyUrl;

    /** 支付成功跳转地址（PC 网站支付用） */
    private String returnUrl;

    /** 过期时间（分钟） */
    private Integer expireMinutes = 30;

    /** 终端类型：PC / H5 / APP / JSAPI / NATIVE(扫码) */
    private String tradeType = "NATIVE";
}
