package com.ecommerce.learn.module.cart.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目视图（含商品信息）
 */
@Data
public class CartVO {

    private Long id;
    private Long skuId;
    private Long spuId;
    private Integer quantity;
    private Integer checked;

    private String title;
    private String image;
    private BigDecimal price;
    private Integer stock;
}
