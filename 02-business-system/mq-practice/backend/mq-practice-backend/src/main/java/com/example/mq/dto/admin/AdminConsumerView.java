package com.example.mq.dto.admin;

import lombok.Builder;
import lombok.Data;

/** 消费者视图：prefetch 与 ackRequired 是定位"消费慢"的关键两个字段 */
@Data
@Builder
public class AdminConsumerView {

    private String queue;

    private String consumerTag;

    /** true = 手动 ACK（本项目全部如此） */
    private boolean ackRequired;

    private int prefetchCount;

    /** up / down / unknown（down 说明消费线程卡住） */
    private String activityStatus;

    private boolean exclusive;

    private String channel;

    /** 消费者所在主机，多实例部署时用来区分是谁在处理 */
    private String peerHost;

    private String connectionName;
}
