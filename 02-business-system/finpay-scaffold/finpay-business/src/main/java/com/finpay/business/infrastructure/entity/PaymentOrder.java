package com.finpay.business.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付订单表。幂等键为 out_trade_no；status 取 {@code PayStatus} 枚举名。
 */
@Data
@TableName("t_payment_order")
public class PaymentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 商户订单号（幂等键） */
    private String outTradeNo;
    /** 渠道交易号 */
    private String tradeNo;
    /** 渠道编码 */
    private String channel;
    /** 商户号 */
    private String merchantNo;
    /** 金额（元） */
    private BigDecimal amount;
    /** 币种 */
    private String currency;
    /** 商品标题 */
    private String subject;
    /** 订单状态 */
    private String status;
    /** 回调原始报文（审计留痕） */
    private String callbackRaw;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 支付完成时间 */
    private LocalDateTime payTime;
}
