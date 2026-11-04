package com.example.mq.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** MQ 总览看板数据 */
@Data
@Builder
public class MqOverviewView {

    /** 各队列深度 / 消费者数 */
    private List<QueueInfoView> queues;

    /* ---------- 生产者侧（mq_message_log） ---------- */
    private long totalSent;
    private long confirmedCount;
    private long returnedCount;
    private long failedCount;

    /* ---------- 消费侧（mq_idempotent_log） ---------- */
    private long idempotentRecords;

    /* ---------- 订单状态分布 ---------- */
    private long orderQueuing;
    private long orderUnpaid;
    private long orderPaid;
    private long orderCancelled;
    private long orderFailed;

    /* ---------- 环境信息 ---------- */
    private long dlqCount;
    private boolean idempotentEnabled;
    private String delayMode;
    private boolean brokerAvailable;
    private String brokerMessage;
    private long timestamp;
}
