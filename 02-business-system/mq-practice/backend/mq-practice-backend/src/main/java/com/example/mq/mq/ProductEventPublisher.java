package com.example.mq.mq;

import com.example.mq.common.message.ProductChangeMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import org.springframework.stereotype.Component;

/**
 * 场景三生产者：商品变更事件发布。
 * <p>
 * 注意这里用的是 <b>topic 交换机 + 一个 routingKey</b>，而不是 fanout。
 * 两种都能实现"一份消息多处消费"，区别是 topic 保留了按主题过滤的能力
 * （比如以后新增"只关心改价"的消费者，用 {@code product.price.#} 就能精确订阅）。
 * <p>
 * 发布方<b>不关心</b>谁订阅、订阅了几个、处理成功与否 —— 这就是"解耦"的真实含义：
 * 新增一个下游（比如搜索引擎、推荐系统）时，商品服务一行代码都不用改。
 */
@Component
public class ProductEventPublisher {

    private final MessageSender messageSender;

    public ProductEventPublisher(MessageSender messageSender) {
        this.messageSender = messageSender;
    }

    public String publish(ProductChangeMessage message) {
        String messageId = message.getMessageId() == null
                ? com.example.mq.common.util.Ids.messageId()
                : message.getMessageId();
        return messageSender.sendWithMessageId(
                messageId,
                MqConstants.PRODUCT_EXCHANGE,
                MqConstants.PRODUCT_CHANGED_ROUTING_KEY,
                message,
                MqScene.PRODUCT_CHANGE,
                String.valueOf(message.getProductId()),
                null,
                null);
    }
}
