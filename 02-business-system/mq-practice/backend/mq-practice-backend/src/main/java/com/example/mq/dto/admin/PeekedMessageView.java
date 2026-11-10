package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 从队列里"看"出来的一条消息。
 * <p>
 * 通过 Management API 的 {@code /get} 接口并指定 {@code ackmode=ack_requeue_true} 实现：
 * 消息被取出读取后立刻放回队列，<b>不会被消费掉</b>，这是排查的正确姿势。
 */
@Data
@Builder
public class PeekedMessageView {

    private int index;

    private String exchange;

    private String routingKey;

    /** 是否重投消息（true 通常意味着之前消费失败过） */
    private boolean redelivered;

    /** 读取时队列里还剩多少条 */
    private long messageCount;

    private String payload;

    private long payloadBytes;

    private String contentType;

    /** AMQP 消息头，含 x-death / x-mq-scene 等排查线索 */
    private Map<String, Object> headers;
}
