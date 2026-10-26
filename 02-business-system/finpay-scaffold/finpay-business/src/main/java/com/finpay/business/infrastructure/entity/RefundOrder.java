package com.finpay.business.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 退款订单表。out_refund_no 为幂等键。 */
@Data
@TableName("t_refund_order")
public class RefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String outRefundNo;
    private String outTradeNo;
    private String tradeNo;
    private String channel;
    private BigDecimal refundAmount;
    private String reason;
    private String status;
    private LocalDateTime createTime;
}
