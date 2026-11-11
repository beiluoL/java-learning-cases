package com.example.ai.cost;

import com.example.ai.common.MoneyScale;
import com.example.ai.cost.dto.CostBreakdown;
import com.example.ai.pricing.entity.ModelPricing;
import com.example.ai.pricing.service.ModelPricingService;
import com.example.ai.token.dto.UsageRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 成本计算：普通输入 / 缓存输入 / 输出三段分别计价再求和，全程 BigDecimal。
 *
 * <p>关键口径：
 * <ul>
 *   <li>cachedInputTokens 是 inputTokens 的<b>子集</b>，必须扣除后再按普通输入单价计费，否则重复收费；</li>
 *   <li>reasoningTokens 默认不单独计费（是否额外收费取决于供应商规则，当前无独立价格字段）；</li>
 *   <li>找不到生效价格时返回 UNPRICED 零成本快照并告警，绝不编造单价；</li>
 *   <li>价格版本与 pricingId 写入账单快照，后续改价不改变历史成本。</li>
 * </ul>
 */
@Service
public class CostCalculator {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int INTERNAL_SCALE = 24;

    private final ModelPricingService pricingService;

    public CostCalculator(ModelPricingService pricingService) {
        this.pricingService = pricingService;
    }

    public CostBreakdown calculate(UsageRecord record, LocalDateTime at) {
        ModelPricing pricing = pricingService.findEffective(
                record.provider(), record.modelName(), record.modelType(), at);

        long cached = Math.min(Math.max(record.cachedInputTokens(), 0), Math.max(record.inputTokens(), 0));
        long normal = Math.max(record.inputTokens(), 0) - cached;
        long output = Math.max(record.outputTokens(), 0);

        if (pricing == null) {
            return new CostBreakdown("UNPRICED", null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    normal, cached, output, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, "USD");
        }

        BigDecimal inputCost = perToken(pricing.getInputPrice(), normal);
        BigDecimal cachedCost = perToken(pricing.getCachedInputPrice(), cached);
        BigDecimal outputCost = perToken(pricing.getOutputPrice(), output);
        BigDecimal total = MoneyScale.normalize(inputCost.add(cachedCost).add(outputCost));

        return new CostBreakdown(
                pricing.getVersion(), pricing.getId(),
                pricing.getInputPrice(), pricing.getOutputPrice(), pricing.getCachedInputPrice(),
                normal, cached, output,
                MoneyScale.normalize(inputCost), MoneyScale.normalize(cachedCost), MoneyScale.normalize(outputCost),
                total, pricing.getCurrency());
    }

    /** 每百万单价 × token 数 / 1_000_000，中间保留 24 位精度避免提前舍入。 */
    private BigDecimal perToken(BigDecimal pricePerMillion, long tokens) {
        return pricePerMillion
                .divide(MILLION, INTERNAL_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(tokens));
    }
}
