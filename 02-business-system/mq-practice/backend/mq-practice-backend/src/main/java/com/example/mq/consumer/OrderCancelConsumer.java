package com.example.mq.consumer;

import com.example.mq.common.message.OrderCancelMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import com.example.mq.config.RabbitMqConfig;
import com.example.mq.mq.AbstractMqConsumer;
import com.example.mq.service.OrderProcessService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 【场景二】订单超时取消消费者。
 *
 * <h3>为什么它消费的是"取消了还能退款"的订单也安全？</h3>
 * 因为取消前会做状态 CAS：{@code UPDATE ... WHERE status = 'UNPAID'}。
 * 用户在第 29 分 59 秒付款、取消消息在第 30 分整到达时，
 * 只有一方能成功，绝不会出现"已支付却被取消"。
 * <p>
 * 这就是延迟消息最重要的设计原则：<b>消息只是定时器，状态判定永远回查数据库</b>。
 */
@Slf4j
@Component
public class OrderCancelConsumer extends AbstractMqConsumer<OrderCancelMessage> {

    private static final String CANCEL_REASON = "超时未支付，系统自动取消";

    private final OrderProcessService orderProcessService;

    public OrderCancelConsumer(OrderProcessService orderProcessService) {
        this.orderProcessService = orderProcessService;
    }

    @RabbitListener(
            queues = MqConstants.ORDER_CANCEL_QUEUE,
            containerFactory = RabbitMqConfig.MANUAL_ACK_FACTORY,
            concurrency = "2-3")
    public void onMessage(OrderCancelMessage payload, Message message, Channel channel) throws IOException {
        handle(payload, message, channel);
    }

    @Override
    protected MqScene scene() {
        return MqScene.ORDER_CANCEL;
    }

    @Override
    protected String bizKeyOf(OrderCancelMessage payload) {
        return payload.getOrderNo();
    }

    @Override
    protected void doConsume(OrderCancelMessage payload, Message message) {
        OrderProcessService.CancelOutcome outcome =
                orderProcessService.cancelOrder(payload.getOrderNo(), CANCEL_REASON);
        if (outcome == OrderProcessService.CancelOutcome.NOT_FOUND) {
            throw new com.example.mq.common.exception.BusinessRejectException(
                    "取消失败：订单不存在 " + payload.getOrderNo());
        }
        log.info("[ORDER-CANCEL] orderNo={} 处理结果={}", payload.getOrderNo(), outcome);
    }
}
