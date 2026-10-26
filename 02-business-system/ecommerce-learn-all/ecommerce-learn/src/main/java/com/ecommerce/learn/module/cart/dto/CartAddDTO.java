package com.ecommerce.learn.module.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车
 */
@Data
public class CartAddDTO {

    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    private Integer quantity;
}
