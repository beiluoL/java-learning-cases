package com.example.ai.token.cost.dto;

import java.math.BigDecimal;

/** 成本计算过程快照，供「查看成本计算过程」与历史价格追溯。金额均为 BigDecimal。 */
public record CostBreakdown(
        String pricingVersion,
        Long pricingId,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        BigDecimal cachedInputPrice,
        long normalInputTokens,
        long cachedInputTokens,
        long outputTokens,
        BigDecimal inputCost,
        BigDecimal cachedCost,
        BigDecimal outputCost,
        BigDecimal totalCost,
        String currency
) {
}
