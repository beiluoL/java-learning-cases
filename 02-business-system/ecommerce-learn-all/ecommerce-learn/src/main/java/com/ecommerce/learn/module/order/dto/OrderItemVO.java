package com.ecommerce.learn.module.order.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细视图
 */
@Data
public class OrderItemVO {

    private Long skuId;
    private Long spuId;
    private String skuName;
    private String image;
    private String specs;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
}
