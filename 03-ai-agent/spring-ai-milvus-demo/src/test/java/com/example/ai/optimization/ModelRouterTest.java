package com.example.ai.optimization;

import com.example.ai.common.AiEnums;
import com.example.ai.config.AiProperties;
import com.example.ai.pricing.entity.ModelPricing;
import com.example.ai.pricing.service.ModelPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 模型路由：只在有生效价格时切换，且只做「降级安全」方向。 */
class ModelRouterTest {

    private AiProperties properties;
    private ModelPricingService pricingService;
    private ModelRouter router;

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setProvider("mock");
        properties.setModel("mock-chat-model");
        properties.getRouting().setL1Model("cheap-model");
        properties.getRouting().setL2Model("mock-chat-model");
        properties.getRouting().setL3Model("strong-model");
        properties.getRouting().setComplexCharThreshold(50);

        ModelPricing pricing = new ModelPricing();
        pricing.setId(1L);
        pricing.setVersion("DEMO-v1");
        pricing.setCurrency("USD");
        pricing.setInputPrice(new BigDecimal("1.00"));
        pricing.setOutputPrice(new BigDecimal("2.00"));
        pricing.setCachedInputPrice(new BigDecimal("0.10"));

        pricingService = mock(ModelPricingService.class);
        when(pricingService.findEffective(anyString(), anyString(), anyString(), any()))
                .thenReturn(pricing);
        router = new ModelRouter(properties, pricingService, Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void simpleQuestionUsesStandardTier() {
        ModelRouter.Decision d = router.route("什么是缓存穿透", "CHAT", AiEnums.ProtectionLevel.NORMAL, false);
        assertEquals(AiEnums.RouteLevel.L2, d.level());
        assertEquals("mock-chat-model", d.model());
        assertFalse(d.downgraded());
    }

    @Test
    void complexQuestionUsesHighTier() {
        ModelRouter.Decision d = router.route("请分析并证明该架构设计的取舍与对比方案", "CHAT",
                AiEnums.ProtectionLevel.NORMAL, false);
        assertEquals(AiEnums.RouteLevel.L3, d.level());
        assertEquals("strong-model", d.model());
    }

    @Test
    void protectionLevelForcesLowCostModel() {
        ModelRouter.Decision d = router.route("随便问一句", "CHAT", AiEnums.ProtectionLevel.LIMIT, false);
        assertEquals(AiEnums.RouteLevel.L1, d.level());
        assertEquals("cheap-model", d.model());
        assertTrue(d.downgraded());
    }

    @Test
    void missingPricingFallsBackToDefaultModel() {
        when(pricingService.findEffective(anyString(), org.mockito.ArgumentMatchers.eq("strong-model"), anyString(), any()))
                .thenReturn(null);
        ModelRouter.Decision d = router.route("请分析并证明该架构设计的取舍与对比方案", "CHAT",
                AiEnums.ProtectionLevel.NORMAL, false);
        assertEquals("mock-chat-model", d.model());
        assertTrue(d.reason().contains("无生效价格"));
    }

    @Test
    void routingDisabledKeepsDefaultModel() {
        properties.getRouting().setEnabled(false);
        ModelRouter.Decision d = router.route("请分析并证明该架构设计的取舍", "CHAT",
                AiEnums.ProtectionLevel.BLOCKED, true);
        assertEquals("mock-chat-model", d.model());
        assertFalse(d.downgraded());
    }

    @Test
    void agentScenePrefersStandardTier() {
        ModelRouter.Decision d = router.route("调用工具查询订单", "AGENT", AiEnums.ProtectionLevel.NORMAL, false);
        assertEquals(AiEnums.RouteLevel.L2, d.level());
    }
}
