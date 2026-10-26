package com.ecommerce.learn.module.order.constant;

/**
 * 订单状态枚举（学习项目采用整型状态码）
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    /** 待支付 */
    public static final int PENDING = 0;
    /** 已支付（待发货） */
    public static final int PAID = 1;
    /** 已发货（待收货） */
    public static final int SHIPPED = 2;
    /** 已完成 */
    public static final int FINISHED = 3;
    /** 已取消 */
    public static final int CANCELLED = 4;
    /** 退款中 */
    public static final int REFUNDING = 5;
    /** 已退款 */
    public static final int REFUNDED = 6;

    public static String desc(int status) {
        return switch (status) {
            case PENDING -> "待支付";
            case PAID -> "已支付";
            case SHIPPED -> "已发货";
            case FINISHED -> "已完成";
            case CANCELLED -> "已取消";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
            default -> "未知";
        };
    }
}
