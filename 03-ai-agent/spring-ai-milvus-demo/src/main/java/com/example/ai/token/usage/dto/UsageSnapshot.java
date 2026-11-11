package com.example.ai.token.usage.dto;

/** Provider Adapter 输出的统一 Usage 快照，屏蔽不同供应商字段语义差异。 */
public record UsageSnapshot(
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long cachedInputTokens,
        long reasoningTokens,
        String usageStatus,
        String actualModel,
        String provider
) {
}
