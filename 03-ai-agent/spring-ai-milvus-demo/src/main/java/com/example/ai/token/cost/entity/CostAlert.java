package com.example.ai.token.cost.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算预警与成本异常事件，message 中禁止写入 Prompt 或密钥。 */
@Data
public class CostAlert {
    private Long id;
    private String requestId;
    private String alertType;
    private String alertLevel;
    private String targetType;
    private String budgetType;
    private String targetId;
    private String message;
    private BigDecimal amount;
    private BigDecimal thresholdValue;
    private BigDecimal currentValue;
    private String severity;
    private String status;
    private Integer occurrenceCount;
    private String fingerprint;
    private LocalDateTime lastSeenAt;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}
