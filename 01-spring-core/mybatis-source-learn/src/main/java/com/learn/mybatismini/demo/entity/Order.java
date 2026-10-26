package com.learn.mybatismini.demo.entity;

import java.math.BigDecimal;

/**
 * 订单实体（对应表 t_order）。
 *
 * <p>这里的 {@code userId / orderNo / amount / status} 同样靠"下划线转驼峰"自动映射：
 * <pre>
 *   USER_ID   -> userId
 *   ORDER_NO  -> orderNo
 *   AMOUNT    -> amount（DECIMAL -> BigDecimal）
 *   STATUS    -> status
 * </pre>
 * OrderMapper 上加了 {@code <cache/>}，用于演示<b>二级缓存</b>。
 */
public class Order {

    private Long id;

    private Long userId;

    private String orderNo;

    private BigDecimal amount;

    private String status;

    public Order() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", userId=" + userId + ", orderNo='" + orderNo + '\''
                + ", amount=" + amount + ", status='" + status + '\'' + '}';
    }
}
