package com.ecommerce.learn.module.product.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPU 详情视图（含 SKU 列表与分类/品牌名）
 */
@Data
public class SpuVO {

    private Long id;
    private String title;
    private Long categoryId;
    private String categoryName;
    private Long brandId;
    private String brandName;
    private String description;
    private String mainImage;
    private String images;
    private Integer status;
    private List<SkuVO> skus;

    /** 价格区间，便于列表/详情展示 */
    private BigDecimal priceMin;
    private BigDecimal priceMax;
}
