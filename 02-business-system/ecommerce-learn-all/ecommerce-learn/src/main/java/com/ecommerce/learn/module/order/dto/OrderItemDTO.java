package com.ecommerce.learn.module.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单项（下单时传入）
 */
@Data
public class OrderItemDTO {

    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    @NotNull(message = "数量不能为空")
    private Integer quantity;
}
