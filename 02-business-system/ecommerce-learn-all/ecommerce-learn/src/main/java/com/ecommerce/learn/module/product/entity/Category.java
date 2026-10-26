package com.ecommerce.learn.module.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类（支持多级：parent_id = 0 表示一级分类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_category")
public class Category extends BaseEntity {

    private String name;
    private Long parentId;
    /** 层级：1 一级，2 二级 ... */
    private Integer level;
    private Integer sort;
    private String icon;
}
