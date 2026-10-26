package com.ecommerce.learn.module.seckill.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动视图（公开）
 */
@Data
public class SeckillActivityVO {

    private Long id;
    private String title;
    private BigDecimal price;
    private Integer stock;
    private Integer sold;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
