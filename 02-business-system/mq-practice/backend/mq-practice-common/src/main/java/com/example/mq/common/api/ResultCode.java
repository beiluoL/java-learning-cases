package com.example.mq.common.api;

import lombok.Getter;

/**
 * 业务状态码字典。
 * <p>
 * 划分区间：0 成功 / 4xx 客户端 / 5xx 服务端 / 4xxx 业务规则 / 5xxx 中间件与第三方。
 * 前端只依赖 code 判断，不依赖 message 文案。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),

    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),
    SERVICE_UNAVAILABLE(503, "依赖服务不可用"),

    /* ============ 业务规则 4xxx ============ */
    STOCK_NOT_ENOUGH(4001, "库存不足，商品已售罄"),
    PRODUCT_NOT_FOUND(4002, "商品不存在"),
    PRODUCT_OFFLINE(4003, "商品已下架"),
    ORDER_NOT_FOUND(4004, "订单不存在"),
    ORDER_STATUS_ILLEGAL(4005, "订单当前状态不允许该操作"),
    REPEAT_SUBMIT(4029, "请勿重复提交"),

    /* ============ 中间件 / MQ 5xxx ============ */
    MQ_SEND_FAILED(5001, "消息发送失败"),
    MQ_CONFIRM_FAILED(5002, "消息未被 Broker 确认"),
    MQ_ROUTE_FAILED(5003, "消息路由失败，无匹配队列"),
    MQ_LISTENER_ERROR(5004, "消息消费异常"),
    REDIS_UNAVAILABLE(5101, "缓存服务不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
