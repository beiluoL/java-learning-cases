package com.example.mq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟 Elasticsearch 的检索索引表。
 * <p>
 * 真实项目里这一层是 ES/OpenSearch 的 index；这里用一张表替代，是为了让
 * 「两个消费者并行处理同一条事件」这件事在本地可观察、可 diff，而不必装一整套 ES。
 */
@Data
@TableName("mq_search_index")
public class MqSearchIndex implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private String productNo;

    private String name;

    private BigDecimal price;

    private Integer status;

    /** = 商品 version，用于丢弃乱序旧消息 */
    private Integer docVersion;

    private LocalDateTime indexedAt;
}
