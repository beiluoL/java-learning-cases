package com.ecommerce.learn.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 后台保存 SKU（含 id 表示更新）
 */
@Data
public class SkuSaveDTO {

    private Long id;
    private String skuCode;
    @NotBlank(message = "SKU 规格不能为空")
    private String specs;
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
    @NotNull(message = "库存不能为空")
    private Integer stock;
    private String image;
    private Integer status = 1;
}
