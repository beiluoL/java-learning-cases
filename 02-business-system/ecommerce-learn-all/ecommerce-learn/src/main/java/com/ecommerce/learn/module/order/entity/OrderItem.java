package com.ecommerce.learn.module.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细（快照：下单时记录商品名/规格/价格，避免后续商品变更影响历史订单）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order_item")
public class OrderItem extends BaseEntity {

    private Long orderId;
    private Long skuId;
    private Long spuId;
    /** 下单时商品标题快照 */
    private String skuName;
    private String image;
    /** 规格 JSON 快照 */
    private String specs;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal totalAmount;
}
