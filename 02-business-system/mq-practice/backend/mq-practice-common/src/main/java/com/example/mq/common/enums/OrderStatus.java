package com.example.mq.common.enums;

import lombok.Getter;

/**
 * 订单状态机。
 * <pre>
 *  QUEUING ──(消费成功)──▶ UNPAID ──(支付)──▶ PAID
 *     │                      │
 *     └──(库存不足/异常)──▶ FAILED
 *                            └──(超时/主动取消)──▶ CANCELLED
 * </pre>
 * 状态流转全部用「条件更新」实现（{@code where status = '期望值'}），
 * 保证并发下只有一次流转成功——这比先 select 再 update 可靠得多。
 */
@Getter
public enum OrderStatus {

    /** 已受理：消息已投递，等待异步落单（前端展示"排队中"） */
    QUEUING("排队中"),

    /** 待支付：订单已落库、库存已扣减、延迟消息已投递 */
    UNPAID("待支付"),

    PAID("已支付"),

    /** 已取消：超时或用户主动取消，库存已回补 */
    CANCELLED("已取消"),

    /** 落单失败：库存不足等业务原因，不会重试 */
    FAILED("创建失败");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    /** 是否终态：终态订单不再接受支付/取消 */
    public boolean isFinal() {
        return this == PAID || this == CANCELLED || this == FAILED;
    }
}
