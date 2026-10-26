package com.ecommerce.learn.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 后台保存品牌
 */
@Data
public class BrandSaveDTO {

    private Long id;
    @NotBlank(message = "品牌名称不能为空")
    private String name;
    private String logo;
    private String description;
}
