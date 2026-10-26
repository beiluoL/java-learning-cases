package com.ecommerce.learn.module.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 选中/取消选中
 */
@Data
public class CartCheckDTO {

    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @NotNull(message = "选中状态不能为空")
    private Integer checked;
}
