package com.ecommerce.learn.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台保存分类
 */
@Data
public class CategorySaveDTO {

    private Long id;
    @NotBlank(message = "分类名称不能为空")
    private String name;
    @NotNull(message = "父级分类不能为空（一级为 0）")
    private Long parentId;
    private Integer level = 1;
    private Integer sort = 0;
    private String icon;
}
