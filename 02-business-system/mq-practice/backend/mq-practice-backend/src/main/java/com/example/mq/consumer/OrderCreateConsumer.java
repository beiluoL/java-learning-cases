package com.example.mq.consumer;

import com.example.mq.common.message.OrderCreateMessage;
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
 * 【场景一】订单创建消费者 —— 削峰填谷的"谷"。
 *
 * <h3>并发与顺序</h3>
 * {@code concurrency = "2-4"} 表示 2 个常驻消费者、高峰可扩到 4 个。
 * 代价是同一订单的两条消息可能被不同线程处理（顺序丢失），
 * 所以这里靠 <b>幂等 + 状态机 CAS</b> 保证正确性，而不是靠队列顺序。
 * 如果业务真的要求严格顺序，只能单消费者 + 单队列（吞吐换顺序）。
 */
@Slf4j
@Component
public class OrderCreateConsumer extends AbstractMqConsumer<OrderCreateMessage> {

    private final OrderProcessService orderProcessService;

    public OrderCreateConsumer(OrderProcessService orderProcessService) {
        this.orderProcessService = orderProcessService;
    }

    @RabbitListener(
            queues = MqConstants.ORDER_CREATE_QUEUE,
            containerFactory = RabbitMqConfig.MANUAL_ACK_FACTORY,
            concurrency = "2-4",
            autoStartup = "true")
    public void onMessage(OrderCreateMessage payload, Message message, Channel channel) throws IOException {
        handle(payload, message, channel);
    }

    @Override
    protected MqScene scene() {
        return MqScene.ORDER_CREATE;
    }

    @Override
    protected String bizKeyOf(OrderCreateMessage payload) {
        return payload.getOrderNo();
    }

    @Override
    protected void doConsume(OrderCreateMessage payload, Message message) {
        OrderProcessService.CreateOutcome outcome = orderProcessService.createOrder(payload);
        if (outcome == OrderProcessService.CreateOutcome.ORDER_NOT_FOUND) {
            // 数据不一致属于"必须有人看一眼"的问题 → 抛业务异常进死信，而不是静默 ack
            throw new com.example.mq.common.exception.BusinessRejectException(
                    "异步落单失败：订单不存在 " + payload.getOrderNo());
        }
        // STOCK_NOT_ENOUGH / SKIPPED 都是业务上的正常终态，正常返回即可（会 ACK）
        log.info("[ORDER-CREATE] orderNo={} 处理结果={}", payload.getOrderNo(), outcome);
    }
}
