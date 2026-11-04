package com.example.mq.mq;

import com.example.mq.common.api.ResultCode;
import com.example.mq.common.exception.BusinessException;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import com.example.mq.common.util.Ids;
import com.example.mq.entity.MqMessageLog;
import com.example.mq.service.MqMessageLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 统一消息发送器：所有生产者都必须经过它，好处是
 * <b>「日志落库 + 唯一 messageId + 可靠性头 + Confirm 关联」四件事一次做齐</b>，
 * 业务代码里不允许再出现裸的 {@code rabbitTemplate.convertAndSend(...)}。
 */
@Slf4j
@Component
public class MessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final MqMessageLogService messageLogService;
    private final ObjectMapper objectMapper;

    public MessageSender(RabbitTemplate rabbitTemplate,
                         MqMessageLogService messageLogService,
                         ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageLogService = messageLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送消息。
     *
     * @param exchange   目标交换机
     * @param routingKey 路由键
     * @param payload    消息体（会被 JSON 序列化）
     * @param scene      业务场景（写日志 + 消息头）
     * @param bizKey     业务键（订单号 / 商品 ID），死信补偿时用于定位数据
     * @param ttlMillis  消息级 TTL（毫秒）。>0 时用于延迟消息；null 表示普通消息
     * @return messageId
     */
    public <T> String send(String exchange, String routingKey, T payload,
                           MqScene scene, String bizKey, Long ttlMillis) {
        return send(exchange, routingKey, payload, scene, bizKey, ttlMillis, null);
    }

    /**
     * 发送消息（带自定义头）。延迟插件方案的 {@code x-delay} 就走这里。
     *
     * @param extraHeaders 追加到 AMQP 消息头的键值对，null 表示不追加
     */
    public <T> String send(String exchange, String routingKey, T payload,
                           MqScene scene, String bizKey, Long ttlMillis,
                           java.util.Map<String, Object> extraHeaders) {
        return sendWithMessageId(Ids.messageId(), exchange, routingKey, payload, scene, bizKey, ttlMillis, extraHeaders);
    }

    /**
     * 指定 messageId 发送 —— 供「事务提交后发布」的场景使用：
     * 先拿到 ID 写进响应体，再在 afterCommit 里真正投递，保证消费者读到的数据已是新版本。
     */
    public <T> String sendWithMessageId(String messageId, String exchange, String routingKey, T payload,
                                        MqScene scene, String bizKey, Long ttlMillis,
                                        java.util.Map<String, Object> extraHeaders) {
        long start = System.currentTimeMillis();

        // ① 先落「发送中」日志：即使 JVM 在下面这一行之后崩掉，也能从表里查出这条消息的去向
        MqMessageLog logEntity = new MqMessageLog();
        logEntity.setMessageId(messageId);
        logEntity.setExchangeName(exchange);
        logEntity.setRoutingKey(routingKey);
        logEntity.setScene(scene.name());
        logEntity.setBizKey(bizKey);
        logEntity.setPayload(toJson(payload));
        logEntity.setRetryCount(0);
        messageLogService.saveSending(logEntity);

        try {
            CorrelationData correlationData = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload,
                    buildPostProcessor(messageId, scene, bizKey, ttlMillis, start, extraHeaders),
                    correlationData);

            messageLogService.lambdaUpdate()
                    .eq(MqMessageLog::getMessageId, messageId)
                    .set(MqMessageLog::getCostMs, System.currentTimeMillis() - start)
                    .update();

            log.debug("[MQ-SEND] scene={} exchange={} rk={} bizKey={} messageId={} cost={}ms",
                    scene, exchange, routingKey, bizKey, messageId, System.currentTimeMillis() - start);
            return messageId;
        } catch (AmqpException e) {
            // 常见原因：broker 不可达、channel 关闭、消息体超限
            messageLogService.markFailed(messageId, e.getMessage());
            throw new BusinessException(ResultCode.MQ_SEND_FAILED,
                    "消息投递失败：" + e.getMessage());
        }
    }

    /** 不带 TTL 的普通发送 */
    public <T> String send(String exchange, String routingKey, T payload, MqScene scene, String bizKey) {
        return send(exchange, routingKey, payload, scene, bizKey, null);
    }

    /**
     * 消费失败后回投「重试队列」。
     * <p>
     * 注意这里用 ack 而不是 nack：原消息已投递一份副本到重试队列，
     * 若再 nack 回原队列就会产生两条重试路径（消息加倍）。
     * 代价是「回投成功但 ack 失败」时原消息会重投一次 —— 所以消费端必须有幂等，
     * 这也是幂等设计不能省的根本原因。
     */
    public void republishForRetry(Object payload, Message originalMessage, int nextRetryCount) {
        MessageProperties origin = originalMessage.getMessageProperties();
        rabbitTemplate.convertAndSend(MqConstants.RETRY_EXCHANGE, origin.getConsumerQueue(), payload, msg -> {
            MessageProperties props = msg.getMessageProperties();
            props.setMessageId(origin.getMessageId());
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setHeader(MqConstants.HEADER_MESSAGE_ID, origin.getMessageId());
            props.setHeader(MqConstants.HEADER_SCENE, origin.getHeader(MqConstants.HEADER_SCENE));
            props.setHeader(MqConstants.HEADER_BIZ_KEY, origin.getHeader(MqConstants.HEADER_BIZ_KEY));
            props.setHeader(MqConstants.HEADER_SENT_AT, origin.getHeader(MqConstants.HEADER_SENT_AT));
            props.setHeader(MqConstants.HEADER_RETRY_COUNT, nextRetryCount);
            return msg;
        });
        log.warn("[MQ-RETRY] 消息回投重试队列 queue={} messageId={} 第 {} 次重试",
                origin.getConsumerQueue(), origin.getMessageId(), nextRetryCount);
    }

    /**
     * 死信补偿：把死信队列里的原始消息按「原交换机 + 原路由键」重新投递。
     * 走原始字节（不是 POJO），避免类型信息丢失导致下游反序列化失败。
     */
    public void republishRaw(String exchange, String routingKey, byte[] body,
                             java.util.Map<String, Object> headers) {
        rabbitTemplate.send(exchange, routingKey, new Message(body, buildRawProperties(headers)));
    }

    private MessageProperties buildRawProperties(java.util.Map<String, Object> headers) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(Ids.messageId());
        if (headers != null) {
            headers.forEach((k, v) -> {
                // x-death 由 broker 维护，重投时必须清掉，否则会累积成一串死信历史；
                // __TypeId__ 必须保留，否则下游反序列化会退化成 LinkedHashMap。
                if (k.startsWith("x-mq-") || "__TypeId__".equals(k) || "x-retry-count".equals(k)) {
                    props.setHeader(k, v);
                }
            });
        }
        props.setHeader(MqConstants.HEADER_RETRY_COUNT, 0);
        return props;
    }

    private MessagePostProcessor buildPostProcessor(String messageId, MqScene scene, String bizKey,
                                                    Long ttlMillis, long start,
                                                    java.util.Map<String, Object> extraHeaders) {
        return message -> {
            MessageProperties props = message.getMessageProperties();
            props.setMessageId(messageId);
            // 持久化：exchange/queue 都是 durable，消息也必须 persistent，否则 broker 重启即丢
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            props.setHeader(MqConstants.HEADER_MESSAGE_ID, messageId);
            props.setHeader(MqConstants.HEADER_SCENE, scene.name());
            props.setHeader(MqConstants.HEADER_BIZ_KEY, bizKey);
            props.setHeader(MqConstants.HEADER_RETRY_COUNT, 0);
            props.setHeader(MqConstants.HEADER_SENT_AT, start);
            if (ttlMillis != null && ttlMillis > 0) {
                // 消息级 TTL：到期后由队列的 x-dead-letter-exchange 转发（延迟消息核心）
                props.setExpiration(String.valueOf(ttlMillis));
            }
            if (extraHeaders != null && !extraHeaders.isEmpty()) {
                extraHeaders.forEach(props::setHeader);
            }
            return message;
        };
    }

    private String toJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.length() > 4000 ? json.substring(0, 4000) + "...(truncated)" : json;
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }
}
