package com.example.mq.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 商品视图：把「数据库 / Redis 缓存 / 检索索引」三份数据并列暴露出来，
 * 目的是让场景三的最终一致性变得肉眼可见。
 */
@Data
@Builder
public class ProductView {

    private Long id;
    private String productNo;
    private String name;
    private java.math.BigDecimal price;
    private Integer status;
    private String statusText;
    private Integer version;

    /* ---------- 数据库侧 ---------- */
    private Integer totalStock;
    private Integer availableStock;

    /* ---------- Redis 侧（秒杀预热库存 + 详情缓存） ---------- */
    private Integer redisStock;
    private String cacheJson;
    private Long cacheTtlSeconds;

    /* ---------- 检索索引侧（模拟 ES） ---------- */
    private String indexJson;
}
