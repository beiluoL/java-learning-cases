package com.ecommerce.learn.module.pay.dto;

import lombok.Data;

/**
 * 支付下单返回信息（模拟支付网关返回的可唤起支付的参数）
 */
@Data
public class PayInfoVO {

    /** 支付渠道：alipay / wechat */
    private String channel;
    /** 商户订单号 */
    private String orderNo;
    /** 唤起支付用的 URL（模拟） */
    private String payUrl;
    /** 二维码内容（模拟扫码支付） */
    private String qrContent;
    /** 第三方交易号（模拟预创建返回） */
    private String prepayTradeNo;
}
