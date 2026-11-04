package com.example.mq.common.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 【场景一】订单创建消息。
 * <p>
 * 生产端只投递「订单意向 + 快照」，不做重业务；真正的库存扣减与落单在消费端完成，
 * 这样接口 RT 可以从「数据库事务 + 扣库存」降到「一次 Redis + 一次 send」，实现削峰填谷。
 * <p>
 * 字段设计原则：<b>消息体自包含</b>。消费端不应再回查生产端数据库才能工作
 * （否则生产端一慢，消费端就全阻塞，MQ 的解耦价值被抵消）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一 ID（UUID），消费端可用于日志串联 */
    private String messageId;

    /** 订单号：既是业务唯一键，也是幂等键 */
    private String orderNo;

    private Long userId;

    private Long productId;

    /** 商品名称快照（下单那一刻的名字，后续商品改名不影响历史订单） */
    private String productName;

    private Integer quantity;

    /** 下单金额快照 */
    private BigDecimal amount;

    /**
     * 支付超时秒数。默认 1800（30 分钟）；
     * 演示时前端可传 10 秒，就能在页面里几十秒内看到「自动取消」全过程。
     */
    private Integer delaySeconds;

    /** 链路追踪 ID，贯穿 生产 → MQ → 消费 */
    private String traceId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
