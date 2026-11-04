package com.example.mq.common.mq;

/**
 * 全项目 MQ 拓扑常量（交换机 / 队列 / 路由键 / 消息头）。
 * <p>
 * 之所以集中在 common 模块：生产端与消费端、乃至运维脚本必须使用同一套名字，
 * 任何一处硬编码字符串拼错都会导致「消息发出去但没人消费」——这是线上最常见的事故之一。
 *
 * <pre>
 * 拓扑总览
 * ─────────────────────────────────────────────────────────────────────────────
 * 场景一 削峰填谷
 *   mq.order.exchange (direct)
 *     └─ order.create ──▶ mq.order.create.queue ──▶ OrderCreateConsumer
 *                               │ 失败重试：mq.retry.exchange → mq.order.create.retry.queue (TTL 5s) → 回投
 *                               └ 超过重试次数：mq.dlx.exchange → mq.dlq.queue
 *
 * 场景二 延迟取消（DLX + TTL 方案，零插件）
 *   mq.order.delay.exchange (direct)
 *     └─ order.delay ──▶ mq.order.delay.queue（无消费者，仅靠 TTL 兜住）
 *                              │ 消息过期/被拒 → x-dead-letter-exchange = mq.order.cancel.exchange
 *                              ▼
 *   mq.order.cancel.exchange (direct)
 *     └─ order.cancel ─▶ mq.order.cancel.queue ──▶ OrderCancelConsumer
 *
 * 场景三 发布订阅
 *   mq.product.exchange (topic)
 *     └─ product.changed ─┬─▶ mq.product.cache.queue  ──▶ CacheSyncConsumer   （更新 Redis）
 *                         └─▶ mq.product.search.queue ──▶ SearchIndexConsumer（更新检索索引）
 *                         两个队列各自独立，一个消费慢/挂了不影响另一个
 * ─────────────────────────────────────────────────────────────────────────────
 * </pre>
 */
public final class MqConstants {

    private MqConstants() {
    }

    /* ==================== 场景一：订单创建（削峰填谷） ==================== */
    public static final String ORDER_EXCHANGE = "mq.order.exchange";
    public static final String ORDER_CREATE_QUEUE = "mq.order.create.queue";
    public static final String ORDER_CREATE_RETRY_QUEUE = "mq.order.create.retry.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create";

    /* ==================== 场景二：订单超时取消（延迟消息） ==================== */
    /** 延迟阶段交换机：生产者把消息投到这里，消息在 delay queue 里"静默"到过期 */
    public static final String ORDER_DELAY_EXCHANGE = "mq.order.delay.exchange";
    /** 长延迟档（> 60s，默认 30 分钟） */
    public static final String ORDER_DELAY_QUEUE = "mq.order.delay.queue";
    public static final String ORDER_DELAY_ROUTING_KEY = "order.delay";
    /**
     * 短延迟档（<= 60s，演示用 10s）。按档位分队列是 DLX+TTL 方案绕开「队头阻塞」的标准做法：
     * 同一队列内只要 TTL 量级接近，先入队的消息就不会长时间挡住后面的消息。
     */
    public static final String ORDER_DELAY_SHORT_QUEUE = "mq.order.delay.short.queue";
    public static final String ORDER_DELAY_SHORT_ROUTING_KEY = "order.delay.short";
    /** 长短档分界（秒）：<= 该值走短延迟队列 */
    public static final int DELAY_SHORT_THRESHOLD_SECONDS = 60;

    /** 过期后死信投递到的真实消费交换机 */
    public static final String ORDER_CANCEL_EXCHANGE = "mq.order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "mq.order.cancel.queue";
    public static final String ORDER_CANCEL_RETRY_QUEUE = "mq.order.cancel.retry.queue";
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /** 可选：rabbitmq_delayed_message_exchange 插件方案的交换机（mq.practice.delay-mode=plugin 时启用） */
    public static final String ORDER_DELAY_PLUGIN_EXCHANGE = "mq.order.delay.plugin.exchange";
    public static final String ORDER_DELAY_PLUGIN_QUEUE = "mq.order.delay.plugin.queue";

    /* ==================== 场景三：商品变更发布订阅 ==================== */
    public static final String PRODUCT_EXCHANGE = "mq.product.exchange";
    public static final String PRODUCT_CACHE_QUEUE = "mq.product.cache.queue";
    public static final String PRODUCT_CACHE_RETRY_QUEUE = "mq.product.cache.retry.queue";
    public static final String PRODUCT_SEARCH_QUEUE = "mq.product.search.queue";
    public static final String PRODUCT_SEARCH_RETRY_QUEUE = "mq.product.search.retry.queue";
    /** topic 通配：product.* 会匹配 product.changed / product.online 等 */
    public static final String PRODUCT_ROUTING_KEY_PATTERN = "product.*";
    public static final String PRODUCT_CHANGED_ROUTING_KEY = "product.changed";

    /* ==================== 公共：重试 & 死信 ==================== */
    /**
     * 重试交换机：所有业务队列共用。routingKey = 目标队列名，
     * 消息在对应 retry queue 里待 retry-delay-ms 毫秒后，由该队列的 DLX 回投业务队列。
     */
    public static final String RETRY_EXCHANGE = "mq.retry.exchange";
    /** 统一死信交换机：业务队列 reject 且不再重试时的最终归宿 */
    public static final String DLX_EXCHANGE = "mq.dlx.exchange";
    public static final String DLQ_QUEUE = "mq.dlq.queue";
    public static final String DLQ_ROUTING_KEY = "mq.dlq";

    /* ==================== 消息头（自定义业务头，避免与 AMQP 内置头冲突） ==================== */
    /** 已重试次数，消费失败时 +1 后回投重试队列 */
    public static final String HEADER_RETRY_COUNT = "x-mq-retry-count";
    /** 消息唯一 ID，用于日志串联与消费端二次去重 */
    public static final String HEADER_MESSAGE_ID = "x-mq-message-id";
    /** 业务场景，便于在死信队列里快速辨认来源 */
    public static final String HEADER_SCENE = "x-mq-scene";
    /** 业务键（订单号 / 商品 ID），死信补偿时用来定位数据 */
    public static final String HEADER_BIZ_KEY = "x-mq-biz-key";
    /** 首次发送时间（毫秒），用于统计端到端延迟 */
    public static final String HEADER_SENT_AT = "x-mq-sent-at";
}
