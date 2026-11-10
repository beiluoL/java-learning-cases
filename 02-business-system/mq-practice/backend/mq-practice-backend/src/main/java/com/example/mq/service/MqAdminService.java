package com.example.mq.service;

import com.example.mq.dto.admin.*;

import java.util.List;

/**
 * RabbitMQ 运维台服务：把 Management HTTP API 的原始 JSON 转成页面好用的视图，
 * 并在服务端完成认证、白名单与写操作保护。
 */
public interface MqAdminService {

    /** 连接状态（不抛异常，broker 挂掉时返回 ok=false + 原因） */
    AdminStatusView status();

    List<AdminQueueView> queues();

    List<AdminExchangeView> exchanges();

    List<AdminBindingView> bindings();

    List<AdminConsumerView> consumers();

    List<AdminConnectionView> connections();

    /** 只读查看队列里的消息（ack_requeue_true，不会消费掉） */
    List<PeekedMessageView> peek(String queue, int count);

    /** 清空队列（写操作，需口令 + 仅限 mq. 前缀） */
    int purge(String queue, String token);

    /** 投递一条测试消息（写操作，需口令） */
    PublishResultView publish(PublishMessageRequest request, String token);
}
