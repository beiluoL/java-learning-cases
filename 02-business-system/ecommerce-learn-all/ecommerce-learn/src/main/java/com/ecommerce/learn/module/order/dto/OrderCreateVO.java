package com.ecommerce.learn.module.order.dto;

import lombok.Data;

/**
 * 创建订单返回
 */
@Data
public class OrderCreateVO {

    private Long orderId;
    private String orderNo;

    public OrderCreateVO(Long orderId, String orderNo) {
        this.orderId = orderId;
        this.orderNo = orderNo;
    }
}
