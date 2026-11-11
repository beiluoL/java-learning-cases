package com.example.ai.token.dto;

/**
 * Provider Adapter 输出的统一 Usage 快照，屏蔽不同供应商的字段语义差异。
 *
 * <p>约定：供应商未返回 Usage 时全部 Token 记 0 且 {@code usageStatus=UNAVAILABLE}，
 * **不允许用估算值填充真实口径**。{@code actualModel} 以供应商响应为准，
 * 可能与请求时指定的模型名不同（别名解析）。</p>
 *
 * @param inputTokens       输入 Token（含缓存命中部分）
 * @param outputTokens      输出 Token
 * @param totalTokens       总 Token
 * @param cachedInputTokens 缓存命中的输入 Token（{@code inputTokens} 的子集）
 * @param reasoningTokens   推理 Token
 * @param usageStatus       Usage 来源：REAL / UNAVAILABLE / MOCK / CACHED
 * @param actualModel       供应商实际返回的模型名
 * @param provider          模型供应商
 */
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
