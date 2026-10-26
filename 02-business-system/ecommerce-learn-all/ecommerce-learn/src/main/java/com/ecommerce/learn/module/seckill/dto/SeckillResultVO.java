package com.ecommerce.learn.module.seckill.dto;

import lombok.Data;

/**
 * 秒杀下单结果
 */
@Data
public class SeckillResultVO {

    private boolean success;
    private Long orderId;
    private String message;
}
