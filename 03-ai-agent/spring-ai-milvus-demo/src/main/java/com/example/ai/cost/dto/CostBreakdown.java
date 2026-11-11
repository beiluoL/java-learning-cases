package com.example.ai.cost.dto;

import java.math.BigDecimal;

/**
 * 成本计算过程快照，供「查看成本计算过程」与历史价格追溯。
 *
 * <p>金额一律 {@link BigDecimal}，**禁止用 double 参与账单计算**。
 * 价格字段是「每百万 Token 单价」（价格表原始值），不是本次实际金额；
 * 本次实际金额见 {@code inputCost} / {@code cachedCost} / {@code outputCost} / {@code totalCost}。</p>
 *
 * @param pricingVersion     命中的价格版本号，账单按此快照可完整复算
 * @param pricingId          命中的价格记录主键
 * @param inputPrice         普通输入 Token 单价（每百万 Token）
 * @param outputPrice        输出 Token 单价（每百万 Token）
 * @param cachedInputPrice   缓存命中输入 Token 单价（每百万 Token）
 * @param normalInputTokens  未命中缓存的输入 Token 数
 * @param cachedInputTokens  命中缓存的输入 Token 数
 * @param outputTokens       输出 Token 数
 * @param inputCost          普通输入部分金额
 * @param cachedCost         缓存命中输入部分金额
 * @param outputCost         输出部分金额
 * @param totalCost          合计金额，等于以上三段之和
 * @param currency           结算货币，当前统一 USD
 */
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
