package com.ecommerce.learn.module.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 后台保存 SPU（含 SKU 列表）
 */
@Data
public class SpuSaveDTO {

    /** 有值表示更新 */
    private Long id;
    @NotBlank(message = "商品标题不能为空")
    private String title;
    @NotNull(message = "分类不能为空")
    private Long categoryId;
    private Long brandId;
    private String description;
    private String mainImage;
    private String images;
    private Integer status = 1;
    @Valid
    private List<SkuSaveDTO> skus;
}
