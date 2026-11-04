package com.example.mq.mq;

import com.example.mq.common.message.OrderCancelMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 方案二：rabbitmq_delayed_message_exchange 插件（需要运维安装插件）。
 * <p>
 * 交换机类型是 {@code x-delayed-message}，消息头 {@code x-delay} 指定延迟毫秒数，
 * 由插件内部的 Mnesia 表负责到期投递，<b>不存在队头阻塞</b>，可以同队列混用任意延迟。
 *
 * <h3>启用方式</h3>
 * <pre>
 *   # 1. 装插件（容器内执行，然后重启容器）
 *   docker exec mq-practice-rabbitmq rabbitmq-plugins enable rabbitmq_delayed_message_exchange
 *   # 2. 把 mq.practice.delay-mode 改成 plugin，重启后端
 * </pre>
 * 与 {@link DlxDelayMessagePublisher} 通过 {@code @ConditionalOnProperty} 互斥，
 * 同一时刻容器里只有一个 {@link DelayMessagePublisher} 实现。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.practice.delay-mode", havingValue = "plugin")
public class PluginDelayMessagePublisher implements DelayMessagePublisher {

    private final MessageSender messageSender;

    public PluginDelayMessagePublisher(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public String publish(OrderCancelMessage payload, int delaySeconds) {
        long delayMillis = Math.max(delaySeconds, 1) * 1000L;
        String messageId = messageSender.send(
                MqConstants.ORDER_DELAY_PLUGIN_EXCHANGE,
                MqConstants.ORDER_DELAY_ROUTING_KEY,
                payload,
                MqScene.ORDER_CANCEL,
                payload.getOrderNo(),
                null,
                Map.of("x-delay", delayMillis));
        log.info("[DELAY-PLUGIN] 订单 {} 的取消闹钟已投递（x-delay={}ms）messageId={}",
                payload.getOrderNo(), delayMillis, messageId);
        return messageId;
    }
}
