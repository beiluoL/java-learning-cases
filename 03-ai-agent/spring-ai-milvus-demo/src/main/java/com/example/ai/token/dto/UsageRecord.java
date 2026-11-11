package com.example.ai.token.dto;

import java.math.BigDecimal;

/**
 * TokenUsageCollector 的统一采集结果：与具体供应商解耦的中立结构。
 *
 * <p>{@code cacheHit=true} 表示本次由响应缓存服务，未发生真实模型调用：
 * 此时 {@code totalCost} 按 0 记账，{@code cacheSavedCost} 记录
 * 「因命中而避免的支出」（基于上次真实账单，**不是新增账单**）。</p>
 *
 * @param requestId         请求唯一 ID，同时是账单幂等键
 * @param traceId           链路追踪 ID
 * @param userId            服务端身份，绝不信任前端传值
 * @param conversationId    会话 ID，可为空
 * @param businessScene     业务场景：CHAT / RAG / EMBEDDING / RERANK / AGENT
 * @param provider          模型供应商
 * @param modelName         供应商实际返回的模型名（不是请求时指定的）
 * @param modelType         模型类型：CHAT / EMBEDDING / RERANK
 * @param inputTokens       输入 Token（含缓存命中部分）
 * @param outputTokens      输出 Token
 * @param totalTokens       总 Token
 * @param cachedInputTokens 缓存命中的输入 Token（{@code inputTokens} 的子集，不重复计费）
 * @param reasoningTokens   推理 Token（按供应商规则，默认不单独计费）
 * @param usageStatus       Usage 来源：REAL / ESTIMATED / UNAVAILABLE / LOCAL / MOCK / CACHED
 * @param requestStatus     请求状态：SUCCESS / FAILED / BUDGET_REJECTED / PROTECTION_BLOCKED / UNKNOWN
 * @param latencyMs         端到端耗时（毫秒）
 * @param errorCode         失败原因码，成功时为 {@code null}
 * @param agentId           Agent 标识，非 Agent 调用为 {@code null}
 * @param knowledgeBaseId   知识库标识，用于 RAG 成本归因
 * @param cacheHit          是否由响应缓存服务（{@code true} 时成本为 0）
 * @param cacheSavedCost    因命中缓存而避免的支出（非新增账单）
 * @param retryCount        模型调用重试次数
 * @param routeLevel        模型路由档位：L1 低成本 / L2 常规 / L3 高能力
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
