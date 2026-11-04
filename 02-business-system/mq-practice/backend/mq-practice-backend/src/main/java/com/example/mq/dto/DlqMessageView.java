package com.example.mq.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 死信队列中的一条消息。
 * 死信不是"垃圾"，而是<b>需要人工决策的未完成业务</b>：
 * 要么修数据后重投（补偿），要么确认业务上放弃（归档）。
 */
@Data
@Builder
public class DlqMessageView {

    /** 在死信队列中的序号（从 1 开始），回放时按它定位 */
    private int index;

    private String messageId;

    /** 原始交换机与路由键（来自 x-death 头） */
    private String originalExchange;
    private String originalRoutingKey;

    /** 死信原因：rejected / expired / maxlen */
    private String reason;

    /** 死亡次数 */
    private long deathCount;

    private String scene;
    private String bizKey;
    private String payload;
}
