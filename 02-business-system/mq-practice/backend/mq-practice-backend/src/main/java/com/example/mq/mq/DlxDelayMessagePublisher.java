package com.example.mq.mq;

import com.example.mq.common.message.OrderCancelMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 方案一：死信队列 + 消息级 TTL（默认，零插件）。
 * <p>
 * 消息投到 {@code mq.order.delay.exchange}，落到 {@code mq.order.delay.queue}。
 * 这个队列<b>故意不配消费者</b>：消息静静躺着，直到 TTL 到期，
 * 由队列的 {@code x-dead-letter-exchange} 转发到取消交换机，被真正的消费者接到。
 *
 * <h3>关键坑：队头阻塞（Head-of-Line Blocking）</h3>
 * RabbitMQ 只检查<b>队首</b>消息是否过期。若队列里先有一条 TTL=30min 的消息，
 * 后面再来一条 TTL=10s 的消息，它必须等前面那条过期后才会被检查 —— 实际延迟远超 10s。
 * 本项目为演示方便允许前端自定义延迟秒数，所以：
 * <ul>
 *   <li>演示/压测建议同一时间只用一个延迟值；</li>
 *   <li>生产要支持任意延迟，请改用延迟插件方案，或为每个延迟档位单独建队列（如 10s/1m/30m）。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.practice.delay-mode", havingValue = "dlx", matchIfMissing = true)
public class DlxDelayMessagePublisher implements DelayMessagePublisher {

    private final MessageSender messageSender;

    public DlxDelayMessagePublisher(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String publish(OrderCancelMessage payload, int delaySeconds) {
        long ttlMillis = Math.max(delaySeconds, 1) * 1000L;
        // 按延迟档位选队列：短延迟独立队列，避免被 30 分钟的消息队头阻塞
        boolean shortDelay = delaySeconds <= MqConstants.DELAY_SHORT_THRESHOLD_SECONDS;
        String routingKey = shortDelay
                ? MqConstants.ORDER_DELAY_SHORT_ROUTING_KEY
                : MqConstants.ORDER_DELAY_ROUTING_KEY;
        String messageId = messageSender.send(
                MqConstants.ORDER_DELAY_EXCHANGE,
                routingKey,
                payload,
                MqScene.ORDER_CANCEL,
                payload.getOrderNo(),
                ttlMillis);
        log.info("[DELAY-DLX] 订单 {} 的取消闹钟已投递（{} 档，{} 秒）messageId={}",
                payload.getOrderNo(), shortDelay ? "短延迟" : "长延迟", delaySeconds, messageId);
        return messageId;
    }
}
