package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 队列视图：把 Management API 里最该看的一屏信息挑出来 */
@Data
@Builder
public class AdminQueueView {

    private String name;

    private String vhost;

    /** classic / quorum（quorum 靠 x-queue-type 参数识别） */
    private String type;

    /** running / idle / flow 等 */
    private String state;

    private boolean durable;

    private boolean autoDelete;

    private boolean exclusive;

    private long messages;

    /** 待投递（真正意义上的"积压"） */
    private long ready;

    /** 已投递未确认 —— 不降说明消费者卡住或忘记 ACK */
    private long unacked;

    private long consumers;

    private double publishRate;

    private double deliverRate;

    private double ackRate;

    /** 重投速率，持续 >0 通常是消费失败在重试 */
    private double redeliverRate;

    private long messageBytes;

    /** 原始参数（x-message-ttl / x-dead-letter-exchange / x-max-length …） */
    private Map<String, Object> arguments;

    /** 从参数里提炼出的 TTL，便于列表直接展示 */
    private Long messageTtlMs;

    /** 是否为本项目声明的队列（mq. 前缀），决定运维台是否允许清空 */
    private boolean managedByProject;
}
