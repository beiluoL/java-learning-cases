package com.example.mq.common.message;

import com.example.mq.common.enums.ProductChangeType;
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
 * 【场景三】商品变更事件（发布/订阅）。
 * <p>
 * 一条事件被投递到 topic exchange，绑定在同一 routingKey 上的两个队列各收到一份副本：
 * 缓存同步消费者更新 Redis，索引同步消费者更新检索索引，二者并行、互不阻塞。
 * <p>
 * {@code version} 是关键：商品短时间内被改两次（改价 100→200→300），MQ 不保证跨队列顺序，
 * 消费者用 {@code version} 做「乐观并发控制」，丢弃比已索引版本更旧的消息，避免最终数据倒退。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductChangeMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;

    private Long productId;

    private String productNo;

    private String name;

    private BigDecimal price;

    /** 0-下架 1-上架 */
    private Integer status;

    private ProductChangeType changeType;

    /** 商品数据版本号，单调递增 */
    private Integer version;

    private String operator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime changeTime;
}
