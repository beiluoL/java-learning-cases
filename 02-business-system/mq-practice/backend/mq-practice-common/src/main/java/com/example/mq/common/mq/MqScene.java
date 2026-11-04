package com.example.mq.common.mq;

import lombok.Getter;

/**
 * 消费场景枚举：同时作为幂等键前缀与幂等日志的 scene 字段。
 * <p>
 * 幂等键统一格式：{@code <scene>:<bizKey>}，例如 {@code ORDER_CREATE:NO20261104213000123}。
 * 加前缀是为了让不同场景复用同一个业务标识时不会互相误判（例如订单号既出现在创建场景也出现在取消场景）。
 */
@Getter
public enum MqScene {

    /** 场景一：异步落单（有消费者） */
    ORDER_CREATE("异步创建订单", true),

    /** 场景二：超时未支付自动取消（有消费者） */
    ORDER_CANCEL("超时取消订单", true),

    /** 场景三：Redis 缓存同步（有消费者） */
    PRODUCT_CACHE_SYNC("商品缓存同步", true),

    /** 场景三：检索索引同步（有消费者） */
    PRODUCT_SEARCH_SYNC("商品索引同步", true),

    /** 场景三：商品变更事件的发布方（无消费者，仅用于消息日志归类） */
    PRODUCT_CHANGE("商品变更事件发布", false);

    private final String description;

    /** 是否存在消费者：决定是否参与「消费幂等 / 故障注入 / 重试死信」这些机制 */
    private final boolean consumable;

    MqScene(String description, boolean consumable) {
        this.description = description;
        this.consumable = consumable;
    }

    /** 组装幂等键 */
    public String idempotentKey(String bizKey) {
        return this.name() + ':' + bizKey;
    }
}
