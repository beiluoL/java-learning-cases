package com.example.mq.service;

import com.example.mq.common.api.PageResult;
import com.example.mq.dto.DlqMessageView;
import com.example.mq.dto.MqOverviewView;
import com.example.mq.dto.QueueInfoView;
import com.example.mq.entity.MqIdempotentLog;
import com.example.mq.entity.MqMessageLog;

import java.util.List;

/**
 * MQ 运维可观测：队列深度、消息账本、幂等记录、死信查看与回放。
 * <p>
 * 生产上这些能力通常交给 RabbitMQ Exporter + Prometheus + Grafana；
 * 这里用接口直接读 broker 与业务表，目的是让学习者在页面上就能看到"消息去哪了"。
 */
public interface MqMonitorService {

    MqOverviewView overview();

    List<QueueInfoView> queues();

    PageResult<MqMessageLog> messages(String scene, String status, long current, long size);

    PageResult<MqIdempotentLog> idempotentLogs(String scene, long current, long size);

    /** 查看死信（只读，不消费） */
    List<DlqMessageView> peekDlq(int limit);

    /** 死信回放：按原始交换机/路由键重新投递，返回成功条数 */
    int replayDlq();
}
