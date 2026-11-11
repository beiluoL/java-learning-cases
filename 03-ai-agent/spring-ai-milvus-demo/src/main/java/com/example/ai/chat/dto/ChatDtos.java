package com.example.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 问答请求/响应 DTO 与 VO。响应字段与 docs/api/AI_COST_API.md 一致。 */
public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * 问答请求。
     *
     * <p>{@code message} 是唯一必填字段；其余 ID 类字段长度上限对齐数据库列宽
     * （{@code VARCHAR(64)}），避免超长值一路带到 DAO 层才由数据库报错。
     * 这些 ID 只用于成本归因与聚合，**不参与鉴权**，身份一律以服务端解析为准。</p>
     *
     * @param message        用户问题，必填，最长 8000 字符
     * @param conversationId 会话 ID，仅用于账单归档
     * @param useRag         是否启用知识库检索；为空按 false 处理
     * @param knowledgeBaseId 知识库标识，用于 RAG 成本归因与知识库级预算
     * @param agentId         Agent 标识，非 Agent 场景留空
     * @param agentTaskId     Agent 任务 ID，非空时启用 Agent 步数/成本闸门
     */
    public record ChatRequest(
            @NotBlank(message = "message 不能为空")
            @Size(max = 8000, message = "message 最长 8000 字符")
            String message,
            @Size(max = 64, message = "conversationId 最长 64 字符")
            String conversationId,
            Boolean useRag,
            @Size(max = 64, message = "knowledgeBaseId 最长 64 字符")
            String knowledgeBaseId,
            @Size(max = 64, message = "agentId 最长 64 字符")
            String agentId,
            @Size(max = 64, message = "agentTaskId 最长 64 字符")
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
