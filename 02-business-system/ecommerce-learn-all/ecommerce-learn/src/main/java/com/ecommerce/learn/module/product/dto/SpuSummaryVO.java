package com.ecommerce.learn.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品列表项视图（SPU 级摘要）
 */
@Data
public class SpuSummaryVO {

    private Long id;
    private String title;
    private String mainImage;
    private String categoryName;
    private String brandName;
    private BigDecimal priceMin;
    private BigDecimal priceMax;
    private Integer salesTotal;
    private Integer status;
}
