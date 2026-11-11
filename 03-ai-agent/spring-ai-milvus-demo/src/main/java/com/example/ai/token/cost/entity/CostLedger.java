package com.example.ai.token.cost.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追加式成本账务流水。历史行不允许 UPDATE/DELETE，
 * 冲正通过新的 ADJUST 反向流水实现，保证账单可审计。
 */
@Data
public class CostLedger {
    private Long id;
    private String entryNo;
    private String entryType;
    private String costKind;
    private String requestId;
    private String traceId;
    private String userId;
    private String agentId;
    private String knowledgeBaseId;
    private String businessScene;
    private String provider;
    private String modelName;
    private Long quantity;
    private BigDecimal amount;
    private String currency;
    private String pricingVersion;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
