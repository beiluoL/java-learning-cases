package com.example.ai.token.usage.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 调用明细分页查询条件。时间范围与分页大小由 Service 强制约束，防止全表扫描。 */
@Data
public class UsageQuery {
    private int page = 1;
    private int size = 20;
    private String userId;
    private String modelName;
    private String businessScene;
    private String requestStatus;
    private String usageStatus;
    private BigDecimal minCost;
    private BigDecimal maxCost;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String sort = "created_at DESC";
}
