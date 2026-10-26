package com.ecommerce.learn.module.product.dto;

import lombok.Data;

/**
 * 商品检索条件
 */
@Data
public class ProductQuery {

    /** 关键词（标题模糊匹配） */
    private String keyword;
    private Long categoryId;
    private Long brandId;
    /** 排序：default / price_asc / price_desc / sales_desc */
    private String sort = "default";

    private long current = 1;
    private long size = 10;
}
