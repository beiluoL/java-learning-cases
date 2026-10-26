package com.ecommerce.learn.module.product.dto;

import lombok.Data;

/**
 * 品牌视图
 */
@Data
public class BrandVO {

    private Long id;
    private String name;
    private String logo;
    private String description;
}
