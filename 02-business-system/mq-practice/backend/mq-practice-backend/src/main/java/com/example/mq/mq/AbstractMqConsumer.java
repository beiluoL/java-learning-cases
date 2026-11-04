package com.example.mq.mq;

import com.example.mq.common.exception.BusinessRejectException;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import com.example.mq.common.util.Ids;
import com.example.mq.config.MqProperties;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

/**
 * 消费端模板方法：把「幂等 → 业务 → ACK / 重试 / 死信」这套固定流程收口到一处。
 * <p>
 * 子类只需要回答三个问题：<b>我是谁（场景）、业务键是什么、业务怎么处理</b>。
 *
 * <h3>消息的四种去向</h3>
 * <pre>
 *  处理成功            → basicAck              （消息从队列删除）
 *  业务性失败(不可重试) → basicReject(false)     （→ DLX → 死信队列，人工/补偿处理）
 *  系统异常(可重试)     → 回投重试队列 + basicAck（TTL 后自动回投，最多 N 次）
 *  重试次数耗尽         → basicReject(false)     （→ 死信队列）
 * </pre>
 *
 * <h3>为什么"回投重试队列"而不是 basicNack(requeue=true)？</h3>
 * requeue=true 会立刻把消息塞回队首，消费端 100% 再次失败 → 死循环打满 CPU，
 * 且没有任何退避。生产上要么用延迟重试队列（本项目做法），要么用 Spring Retry 的
 * 指数退避 + RepublishMessageRecoverer。
 */
@Slf4j
public abstract class AbstractMqConsumer<T> {

    @Autowired
    protected MessageSender messageSender;

    @Autowired
    protected IdempotentGuard idempotentGuard;

    @Autowired
    protected MqProperties mqProperties;

    @Autowired
    protected ChaosSwitch chaosSwitch;

    /** 消费场景（幂等键前缀 + 日志） */
    protected abstract MqScene scene();

    /** 从消息体提取业务键，作为幂等依据 */
    protected abstract String bizKeyOf(T payload);

    /** 真正的业务处理；抛 {@link BusinessRejectException} 表示不可重试 */
    protected abstract void doConsume(T payload, Message message) throws Exception;

    /**
     * 统一消费入口。子类的 {@code @RabbitListener} 方法只需调用它。
     */
    protected void handle(T payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = resolveMessageId(message);
        String bizKey;
        try {
            bizKey = bizKeyOf(payload);
        } catch (Exception e) {
            log.error("[CONSUME] 无法解析业务键，直接进死信 messageId={}", messageId, e);
            rejectToDlq(channel, deliveryTag);
            return;
        }

        long begin = System.currentTimeMillis();
        try {
            // ---------- ① 幂等校验：重复消息直接 ack，不重复执行业务 ----------
            if (!idempotentGuard.tryAcquire(scene(), bizKey, messageId, payload)) {
                ack(channel, deliveryTag);
                log.info("[CONSUME SKIP] scene={} bizKey={} 重复消息已忽略", scene(), bizKey);
                return;
            }

            // ---------- ② 执行业务 ----------
            try {
                // 教学用故障注入：放在幂等之后、业务之前，保证重试链路可完整观测
                chaosSwitch.maybeFail(scene());
                doConsume(payload, message);
                idempotentGuard.markSuccess(scene(), bizKey);
                ack(channel, deliveryTag);
                log.info("[CONSUME OK] scene={} bizKey={} messageId={} cost={}ms",
                        scene(), bizKey, Ids.shortId(messageId), System.currentTimeMillis() - begin);
            } catch (BusinessRejectException e) {
                // 业务规则不允许继续（如库存不足）：重试无意义，进死信
                idempotentGuard.markFailure(scene(), bizKey);
                rejectToDlq(channel, deliveryTag);
                log.warn("[CONSUME REJECT] scene={} bizKey={} 业务性失败，进入死信队列：{}",
                        scene(), bizKey, e.getMessage());
            } catch (Exception e) {
                // 系统异常：可重试
                idempotentGuard.markFailure(scene(), bizKey);
                retryOrDeadLetter(payload, message, channel, deliveryTag, bizKey, e);
            }
        } catch (Exception e) {
            // 兜底：幂等组件自身故障（Redis/DB 不可用）时，不要让消息凭空消失
            log.error("[CONSUME ERROR] scene={} bizKey={} 消费框架异常", scene(), bizKey, e);
            rejectToDlq(channel, deliveryTag);
        }
    }

    /** 重试判断：未超限 → 回投重试队列并 ack 原消息；超限 → 进死信 */
    private void retryOrDeadLetter(T payload, Message message, Channel channel, long deliveryTag,
                                   String bizKey, Exception cause) throws IOException {
        int current = retryCount(message);
        int max = mqProperties.getMaxRetryCount();
        if (current >= max) {
            log.error("[CONSUME DLQ] scene={} bizKey={} 已达最大重试次数 {}，进入死信队列",
                    scene(), bizKey, max, cause);
            rejectToDlq(channel, deliveryTag);
            return;
        }
        try {
            messageSender.republishForRetry(payload, message, current + 1);
            // 先投递副本再 ack 原消息：最坏情况是重复投递（幂等兜底），而不是消息丢失
            ack(channel, deliveryTag);
            log.warn("[CONSUME RETRY] scene={} bizKey={} 第 {}/{} 次重试，原因：{}",
                    scene(), bizKey, current + 1, max, cause.getMessage());
        } catch (Exception e) {
            log.error("[CONSUME RETRY-FAIL] scene={} bizKey={} 回投重试队列失败，转入死信",
                    scene(), bizKey, e);
            rejectToDlq(channel, deliveryTag);
        }
    }

    private void ack(Channel channel, long deliveryTag) throws IOException {
        channel.basicAck(deliveryTag, false);
    }

    private void rejectToDlq(Channel channel, long deliveryTag) throws IOException {
        // requeue=false + 队列配置了 x-dead-letter-exchange → 消息进入 mq.dlq.queue
        channel.basicReject(deliveryTag, false);
    }

    private int retryCount(Message message) {
        Object raw = message.getMessageProperties().getHeader(MqConstants.HEADER_RETRY_COUNT);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        Object legacy = message.getMessageProperties().getHeader("x-retry-count");
        if (legacy instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String resolveMessageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId != null) {
            return messageId;
        }
        Object header = message.getMessageProperties().getHeader(MqConstants.HEADER_MESSAGE_ID);
        return header == null ? Ids.messageId() : String.valueOf(header);
    }
}
