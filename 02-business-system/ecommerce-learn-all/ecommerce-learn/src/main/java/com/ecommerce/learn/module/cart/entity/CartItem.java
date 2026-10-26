package com.ecommerce.learn.module.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车条目（以用户 + SKU 为维度）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_cart_item")
public class CartItem extends BaseEntity {

    private Long userId;
    private Long skuId;
    private Long spuId;
    /** 数量 */
    private Integer quantity;
    /** 是否选中：1 选中，0 未选 */
    private Integer checked;
}
