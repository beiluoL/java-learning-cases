package com.ecommerce.learn.module.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SKU（库存量单位）：SPU 下具体的可售卖单元（不同规格组合）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_sku")
public class Sku extends BaseEntity {

    private Long spuId;
    /** 商家自定义 SKU 编码 */
    private String skuCode;
    /** 规格 JSON，如 {"颜色":"红色","容量":"256G"} */
    private String specs;
    private BigDecimal price;
    /** 实际库存 */
    private Integer stock;
    /** 锁定库存（已下单未支付，防止超卖） */
    private Integer lockedStock;
    private String image;
    /** 销量 */
    private Integer sales;
    /** 状态：1 在售，0 下架 */
    private Integer status;
}
