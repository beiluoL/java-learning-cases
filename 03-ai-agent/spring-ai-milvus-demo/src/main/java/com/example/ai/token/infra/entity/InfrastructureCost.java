package com.example.ai.token.infra.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 基础设施成本：区分直接计量(METERED)与分摊(ALLOCATED)与固定成本(FIXED)。 */
@Data
public class InfrastructureCost {
    private Long id;
    private String resourceType;
    private String resourceName;
    private String provider;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal amount;
    private String currency;
    private String allocationMethod;
    private BigDecimal allocatedAmount;
    private Long requestCount;
    private Integer demoFlag;
    private String remark;
    private LocalDateTime createdAt;
}
