package com.ecommerce.learn.module.product.dto;

import lombok.Data;

import java.util.List;

/**
 * 分类视图（含子分类树）
 */
@Data
public class CategoryVO {

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private String icon;
    private List<CategoryVO> children;
}
