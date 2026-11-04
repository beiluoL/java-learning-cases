package com.example.mq.dto;

import com.example.mq.common.enums.OrderStatus;
import com.example.mq.entity.MqOrder;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 订单视图对象。
 * <p>
 * 为什么不直接返回实体？
 * <ol>
 *   <li>实体字段是数据库结构的镜像，接口契约应独立演进；</li>
 *   <li>前端倒计时需要 {@code remainingSeconds}，而它取决于<b>服务端</b>时间——
 *       直接用浏览器时间和 expireTime 相减，用户改系统时间就露馅了；</li>
 *   <li>状态文案（UNPAID→待支付）不应该让前端维护一份字典。</li>
 * </ol>
 */
@Data
@Builder
public class OrderView {

    private Long id;
    private String orderNo;
    private Long userId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
    private String statusText;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private Integer delaySeconds;

    /** 距离自动取消还剩多少秒；<=0 表示已到期或非待支付状态 */
    private long remainingSeconds;

    public static OrderView from(MqOrder order) {
        if (order == null) {
            return null;
        }
        OrderStatus status = parse(order.getStatus());
        long remaining = 0L;
        if (status == OrderStatus.UNPAID && order.getExpireTime() != null) {
            remaining = Math.max(0L, Duration.between(LocalDateTime.now(), order.getExpireTime()).getSeconds());
        }
        return OrderView.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .productId(order.getProductId())
                .productName(order.getProductName())
                .quantity(order.getQuantity())
                .amount(order.getAmount())
                .status(order.getStatus())
                .statusText(status.getDescription())
                .createTime(order.getCreateTime())
                .expireTime(order.getExpireTime())
                .payTime(order.getPayTime())
                .cancelTime(order.getCancelTime())
                .cancelReason(order.getCancelReason())
                .delaySeconds(order.getDelaySeconds())
                .remainingSeconds(remaining)
                .build();
    }

    private static OrderStatus parse(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (Exception e) {
            return OrderStatus.FAILED;
        }
    }
}
