package com.example.mq.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 投递测试消息（写操作，需运维口令） */
@Data
public class PublishMessageRequest {

    /** 交换机名；空字符串表示默认交换机（routingKey 即队列名） */
    private String exchange = "";

    @NotBlank(message = "routingKey 不能为空")
    private String routingKey;

    @NotBlank(message = "消息体不能为空")
    private String payload;

    /** 仅用于在消息头里留痕，便于在业务日志里对上号 */
    private String source = "mq-console";

    /**
     * 消息类型（简单类名），用于给消息加 {@code __TypeId__} 头。
     * <p>
     * 为什么必须显式指定：本项目的消费端是「强类型方法签名」
     * （{@code onMessage(ProductChangeMessage payload, ...)}），Spring AMQP 靠
     * {@code __TypeId__} 头决定反序列化成哪个类。从控制台手发的裸 JSON 没有这个头，
     * 消费端会因「无法确定目标类型」直接拒绝 —— 消息会进死信。
     * 可选值：ProductChangeMessage / OrderCreateMessage / OrderCancelMessage / 空（原始消息）
     */
    private String messageType;
}
