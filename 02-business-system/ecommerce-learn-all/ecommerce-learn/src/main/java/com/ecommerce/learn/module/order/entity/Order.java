package com.ecommerce.learn.module.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.ecommerce.learn.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class Order extends BaseEntity {

    /** 订单编号（业务可读，见 OrderNoGenerator） */
    private String orderNo;
    private Long userId;
    /** 商品总金额 */
    private BigDecimal totalAmount;
    /** 实付金额 */
    private BigDecimal payAmount;
    /** 状态：见 OrderStatus */
    private Integer status;
    /** 支付类型：alipay / wechat / balance */
    private String payType;
    /** 第三方支付流水号 */
    private String tradeNo;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;

    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime finishTime;
    private LocalDateTime closeTime;

    /** 物流公司 */
    private String logisticsCompany;
    /** 物流单号 */
    private String logisticsNo;
}
