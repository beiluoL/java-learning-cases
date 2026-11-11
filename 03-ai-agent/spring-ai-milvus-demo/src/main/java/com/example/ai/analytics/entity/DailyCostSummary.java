package com.example.ai.analytics.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每日成本汇总（按维度预聚合），用于趋势与同比分析，避免统计时全表扫描。 */
@Data
public class DailyCostSummary {
    /** 主键 */
    private Long id;
    /** 汇总日期(UTC) */
    private LocalDate summaryDate;
    /** 维度：ALL / MODEL / SCENE / USER / AGENT */
    private String dimension;
    /** 维度取值 */
    private String dimensionKey;
    /** 请求次数 */
    private Long requestCount;
    /** 成功次数 */
    private Long successCount;
    /** 失败次数 */
    private Long failedCount;
    /** 输入 Token */
    private Long inputTokens;
    /** 输出 Token */
    private Long outputTokens;
    /** 缓存命中输入 Token */
    private Long cachedTokens;
    /** 总 Token */
    private Long totalTokens;
    /** 总成本 */
    private BigDecimal totalCost;
    /** 缓存节省成本 */
    private BigDecimal cacheSavedCost;
    /** 创建时间(UTC) */
    private LocalDateTime createdAt;
    /** 更新时间(UTC) */
    private LocalDateTime updatedAt;
}
