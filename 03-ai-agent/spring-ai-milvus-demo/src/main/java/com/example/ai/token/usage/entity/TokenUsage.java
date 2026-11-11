package com.example.ai.token.usage.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 调用明细与成本账单实体，与 ai_token_usage 表一一对应。 */
@Data
public class TokenUsage {
    private Long id;
    private String requestId;
    private String traceId;
    private String userId;
    private String conversationId;
    private String agentId;
    private String knowledgeBaseId;
    private String businessScene;
    private String provider;
    private String modelName;
    private String modelType;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long cachedInputTokens;
    private Long reasoningTokens;
    private String usageStatus;
    private String requestStatus;
    private Integer cacheHit;
    private Long latencyMs;
    private BigDecimal inputCost;
    private BigDecimal cachedInputCost;
    private BigDecimal outputCost;
    private BigDecimal totalCost;
    private BigDecimal cacheSavedCost;
    private String currency;
    private String pricingVersion;
    private Long pricingId;
    private String errorCode;
    private Integer retryCount;
    private String routeLevel;
    private LocalDateTime createdAt;
}
