package com.example.ai.token.cost;

import com.example.ai.common.MoneyScale;
import com.example.ai.token.cost.dto.CostBreakdown;
import com.example.ai.token.pricing.entity.ModelPricing;
import com.example.ai.token.pricing.service.ModelPricingService;
import com.example.ai.token.usage.dto.UsageRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 成本计算单测：缓存 Token 不重复计费、BigDecimal 精度、缺价格时 UNPRICED。 */
class CostCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 0, 0);

    private ModelPricing pricing(String input, String output, String cached) {
        ModelPricing p = new ModelPricing();
        p.setId(7L);
        p.setVersion("DEMO-v1");
        p.setCurrency("USD");
        p.setInputPrice(new BigDecimal(input));
        p.setOutputPrice(new BigDecimal(output));
        p.setCachedInputPrice(new BigDecimal(cached));
        return p;
    }

    private UsageRecord record(long in, long out, long cached) {
        return new UsageRecord("req-1", "trc-1", "demo-user", null, "CHAT", "mock",
                "mock-chat-model", "CHAT", in, out, in + out, cached, 0,
                "REAL", "SUCCESS", 10L, null,
                null, null, false, BigDecimal.ZERO, 0, "L2");
    }

    private CostCalculator calculator(ModelPricing p) {
        ModelPricingService service = mock(ModelPricingService.class);
        when(service.findEffective(anyString(), anyString(), anyString(), any())).thenReturn(p);
        return new CostCalculator(service);
    }

    @Test
    void cachedTokensAreInputSubsetAndNeverDoubleCharged() {
        // 1000 输入(其中 400 缓存) + 500 输出；普通输入 600，缓存 400
        CostBreakdown b = calculator(pricing("1.00", "2.00", "0.10"))
                .calculate(record(1000, 500, 400), NOW);

        assertEquals(600, b.normalInputTokens());
        assertEquals(400, b.cachedInputTokens());
        assertEquals(0, new BigDecimal("0.00060000").compareTo(b.inputCost()));
        assertEquals(0, new BigDecimal("0.00004000").compareTo(b.cachedCost()));
        assertEquals(0, new BigDecimal("0.00100000").compareTo(b.outputCost()));
        assertEquals(0, new BigDecimal("0.00164000").compareTo(b.totalCost()));
        assertEquals("DEMO-v1", b.pricingVersion());
        assertEquals(7L, b.pricingId());
    }

    @Test
    void cachedTokensCannotExceedInputTokens() {
        CostBreakdown b = calculator(pricing("1.00", "2.00", "0.10"))
                .calculate(record(100, 0, 999), NOW);
        assertEquals(100, b.cachedInputTokens());
        assertEquals(0, b.normalInputTokens());
    }

    @Test
    void missingPriceIsMarkedUnpricedNeverFabricated() {
        CostBreakdown b = calculator(null).calculate(record(100, 50, 0), NOW);
        assertEquals("UNPRICED", b.pricingVersion());
        assertEquals(0, BigDecimal.ZERO.compareTo(b.totalCost()));
    }

    @Test
    void moneyScaleRoundTripIsExact() {
        BigDecimal amount = new BigDecimal("0.00345678");
        assertEquals(0, amount.compareTo(MoneyScale.fromUnits(MoneyScale.toUnits(amount))));
    }
}
