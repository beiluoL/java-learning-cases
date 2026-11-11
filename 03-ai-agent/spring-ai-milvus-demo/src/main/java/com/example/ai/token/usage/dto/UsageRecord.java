package com.example.ai.token.usage.dto;

import java.math.BigDecimal;

/**
 * TokenUsageCollector 的统一采集结果：与具体供应商解耦的中立结构。
 *
 * <p>cacheHit=true 表示本次由响应缓存服务，未发生真实模型调用：
 * 此时 cost 按 0 记账，cacheSavedCost 记录「因命中而避免的支出」（基于上次真实账单）。
 */
public record UsageRecord(
        String requestId,
        String traceId,
        String userId,
        String conversationId,
        String businessScene,
        String provider,
        String modelName,
        String modelType,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long cachedInputTokens,
        long reasoningTokens,
        String usageStatus,
        String requestStatus,
        long latencyMs,
        String errorCode,
        String agentId,
        String knowledgeBaseId,
        boolean cacheHit,
        BigDecimal cacheSavedCost,
        int retryCount,
        String routeLevel
) {
}
