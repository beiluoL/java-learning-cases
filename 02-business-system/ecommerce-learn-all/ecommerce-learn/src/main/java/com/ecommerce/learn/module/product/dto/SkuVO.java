package com.ecommerce.learn.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 视图
 */
@Data
public class SkuVO {

    private Long id;
    private String skuCode;
    /** 规格 JSON 字符串 */
    private String specs;
    private BigDecimal price;
    private Integer stock;
    private String image;
    private Integer sales;
    private Integer status;
}
