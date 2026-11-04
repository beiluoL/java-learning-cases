package com.example.mq.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单（场景一落单结果 + 场景二延迟取消对象） */
@Data
@TableName("mq_order")
public class MqOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务唯一键，同时也是 MQ 幂等键 */
    private String orderNo;

    private Long userId;

    private Long productId;

    private String productName;

    private Integer quantity;

    private BigDecimal amount;

    /** 见 {@link com.example.mq.common.enums.OrderStatus} */
    private String status;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    /** 本单支付超时秒数（演示时可以是 10 秒） */
    private Integer delaySeconds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
