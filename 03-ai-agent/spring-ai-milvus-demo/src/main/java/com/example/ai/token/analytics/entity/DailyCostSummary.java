package com.example.ai.token.analytics.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 每日成本汇总（按维度预聚合），用于趋势与同比分析，避免统计时全表扫描。 */
@Data
public class DailyCostSummary {
    private Long id;
    private LocalDate summaryDate;
    private String dimension;
    private String dimensionKey;
    private Long requestCount;
    private Long successCount;
    private Long failedCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long cachedTokens;
    private Long totalTokens;
    private BigDecimal totalCost;
    private BigDecimal cacheSavedCost;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
