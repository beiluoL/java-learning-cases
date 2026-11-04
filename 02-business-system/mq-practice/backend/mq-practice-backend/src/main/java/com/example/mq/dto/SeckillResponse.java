package com.example.mq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秒杀下单的即时返回。
 * <p>
 * 注意这里 <b>不返回订单详情</b>，只返回"排队凭证"——这正是削峰填谷的接口语义：
 * 服务端只做三件极快的事（Redis 预扣、落排队单、投递消息），
 * 真正耗时的事务性工作交给消费者，接口 RT 因此稳定在毫秒级。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResponse {

    private String orderNo;

    /** 固定为 QUEUING，前端据此进入"排队中"轮询 */
    private String status;

    /** 提示文案 */
    private String message;

    /** Redis 预扣后的剩余库存（null 表示未预热、以数据库为准） */
    private Integer remainingStock;

    /** 本次请求服务端耗时（毫秒），用于对比异步前后的差异 */
    private long costMs;

    /** 消息 ID，便于在监控页按 ID 追踪 */
    private String messageId;
}
