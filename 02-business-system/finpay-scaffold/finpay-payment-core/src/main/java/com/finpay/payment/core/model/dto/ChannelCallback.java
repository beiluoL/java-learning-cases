package com.finpay.payment.core.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 标准化渠道回调。任何渠道的回调报文经 {@code PaymentChannel.parseCallback} 后，
 * 统一转换为此对象，业务层据此更新订单状态——无需关心支付宝/微信的字段差异。
 */
@Data
public class ChannelCallback {

    /** 商户订单号 */
    private String outTradeNo;

    /** 渠道交易号 */
    private String tradeNo;

    /** 金额（元） */
    private BigDecimal amount;

    /** 支付结果：SUCCESS / FAIL */
    private String result;

    /** 渠道编码 */
    private String channel;

    /** 完成时间（yyyy-MM-dd HH:mm:ss） */
    private String finishTime;

    /** 渠道原始报文（用于审计留痕） */
    private String raw;

    /** 渠道携带的附加参数 */
    private Map<String, String> extra;
}
