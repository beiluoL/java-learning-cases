package com.example.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 队列运行指标 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueInfoView {

    private String name;

    /** 就绪消息数（含延迟队列里尚未到期的消息） */
    private long messageCount;

    /** 活跃消费者数 */
    private long consumerCount;

    /** 本项目的角色说明，便于前端展示拓扑 */
    private String role;
}
