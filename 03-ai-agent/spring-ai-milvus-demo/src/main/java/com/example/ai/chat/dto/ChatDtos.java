package com.example.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 问答请求/响应 DTO 与 VO。响应字段与 docs/api/AI_COST_API.md 一致。 */
public final class ChatDtos {

    private ChatDtos() {
    }

    public record ChatRequest(
            @NotBlank(message = "message 不能为空")
            @Size(max = 8000, message = "message 最长 8000 字符")
            String message,
            String conversationId,
            Boolean useRag,
            /** 知识库标识：仅用于成本归因与知识库预算，不参与鉴权。 */
            String knowledgeBaseId,
            /** Agent 任务标识：非 Agent 场景留空；Agent 场景用于任务级成本聚合。 */
            String agentId,
            /** Agent 任务 ID：配合 agentId 使用，同一任务多次调用会聚合成一条追踪。 */
            String agentTaskId
    ) {
    }

    /**
     * 问答响应。新增字段均为「成本治理」可解释性字段：
     * routeLevel 说明实际选了哪档模型、cacheHit 说明本次是否完全没调用模型、
     * optimization 说明本次在 Prompt / 上下文 / 输出长度上做了什么压缩。
     */
    public record ChatResponseVO(
            String requestId,
            String traceId,
            String answer,
            String model,
            String provider,
            String routeLevel,
            boolean cacheHit,
            String cacheSavedCost,
            int protectionLevel,
            UsageVO usage,
            CostVO cost,
            BudgetVO budget,
            OptimizationVO optimization,
            long latencyMs
    ) {
    }

    public record UsageVO(long inputTokens, long outputTokens, long totalTokens,
                          long cachedInputTokens, long reasoningTokens, String usageStatus) {
    }

    public record CostVO(String inputCost, String outputCost, String totalCost,
                         String currency, String pricingVersion) {
    }

    public record BudgetVO(String reserved, String settled, String remaining) {
    }

    /** 本次请求的压缩效果；measureKind 明确是估算还是实测口径。 */
    public record OptimizationVO(int promptTokensBefore, int promptTokensAfter, int savedTokens,
                                 String savedCost, String measureKind) {
    }
}
