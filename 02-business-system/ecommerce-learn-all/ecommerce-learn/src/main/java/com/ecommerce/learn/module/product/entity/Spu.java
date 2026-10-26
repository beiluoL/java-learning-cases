package com.ecommerce.learn.module.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SPU（标准化产品单元）：一个商品款式的聚合，下挂多个 SKU
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_spu")
public class Spu extends BaseEntity {

    private String title;
    private Long categoryId;
    private Long brandId;
    private String description;
    /** 主图 URL */
    private String mainImage;
    /** 图集，JSON 数组字符串，如 ["url1","url2"] */
    private String images;
    /** 状态：1 上架，0 下架 */
    private Integer status;
}
