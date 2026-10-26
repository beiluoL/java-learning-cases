package com.ecommerce.learn.module.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车数量
 */
@Data
public class CartUpdateDTO {

    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    private Integer quantity;
}
