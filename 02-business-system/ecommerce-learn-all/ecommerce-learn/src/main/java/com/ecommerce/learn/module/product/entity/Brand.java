package com.ecommerce.learn.module.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_brand")
public class Brand extends BaseEntity {

    private String name;
    private String logo;
    private String description;
}
