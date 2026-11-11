package com.example.ai.cost;

import com.example.ai.alert.CostAlertService;
import com.example.ai.analytics.mapper.CostAnalyticsMapper;
import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.mapper.BudgetPolicyMapper;
import com.example.ai.common.AiEnums;
import com.example.ai.identity.UserIdentityProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 消费预测：公式与阈值必须可复核，超支要提前告警。 */
class SpendForecastServiceTest {

    /** 2026-11-11 12:00 UTC → 本月已过 10.5 天，当月 30 天。 */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);

    private CostAnalyticsMapper analyticsMapper;
    private BudgetPolicyMapper policyMapper;
    private CostAlertService alertService;
    private SpendForecastService service;

    @BeforeEach
    void setUp() {
        analyticsMapper = mock(CostAnalyticsMapper.class);
        policyMapper = mock(BudgetPolicyMapper.class);
        alertService = mock(CostAlertService.class);
        UserIdentityProvider identity = mock(UserIdentityProvider.class);
        when(identity.currentUserId()).thenReturn("demo-user");

        BudgetPolicy monthly = new BudgetPolicy();
        monthly.setBudgetType("MONTHLY");
        monthly.setTargetId("demo-user");
        monthly.setMonthlyLimit(new BigDecimal("100.00"));
        when(policyMapper.findScope(eq("MONTHLY"), anyString())).thenReturn(monthly);

        BudgetPolicy daily = new BudgetPolicy();
        daily.setBudgetType("DAILY");
        daily.setTargetId("demo-user");
        daily.setDailyLimit(new BigDecimal("5.00"));
        when(policyMapper.findScope(eq("DAILY"), anyString())).thenReturn(daily);

        service = new SpendForecastService(analyticsMapper, policyMapper, alertService, identity, CLOCK);
    }

    @Test
    void projectsMonthCostFromElapsedTime() {
        when(analyticsMapper.overview(any(), any()))
                .thenReturn(Map.of("totalCost", new BigDecimal("10.00")))
                .thenReturn(Map.of("totalCost", new BigDecimal("1.00")));

        SpendForecastService.Forecast f = service.monthly();
        // 10 / 10.5 天 × 30 天 ≈ 28.57（除数保留 8 位会产生 28.57142850 的尾差）
        BigDecimal projected = new BigDecimal(f.projectedCost());
        assertTrue(projected.compareTo(new BigDecimal("28.57")) > 0
                        && projected.compareTo(new BigDecimal("28.58")) < 0,
                "预测值应落在 28.57~28.58 之间，实际 " + projected);
        assertFalse(f.willExceed(), "28.57 未超过 100 的月预算");
        assertEquals(0.1d, f.usageRate(), 1e-9);
        verify(alertService, never()).alert(eq(AiEnums.AlertType.FORECAST_EXHAUST), any(), any(),
                any(), anyString(), any(), any(), any());
    }

    @Test
    void warnsWhenProjectionExceedsMonthlyBudget() {
        when(analyticsMapper.overview(any(), any()))
                .thenReturn(Map.of("totalCost", new BigDecimal("50.00")))
                .thenReturn(Map.of("totalCost", new BigDecimal("5.00")));

        SpendForecastService.Forecast f = service.monthly();
        assertTrue(f.willExceed());
        assertTrue(new BigDecimal(f.projectedCost()).compareTo(new BigDecimal("100")) > 0);
        verify(alertService).alert(eq(AiEnums.AlertType.FORECAST_EXHAUST), any(), any(), any(),
                anyString(), any(), any(), any());
    }

    @Test
    void dailyExhaustionEtaUsesHourlyBurnRate() {
        when(analyticsMapper.overview(any(), any()))
                .thenReturn(Map.of("totalCost", new BigDecimal("2.00")));
        // 已过 12 小时，花费 2.00 → 每小时 0.1667；剩余 3.00 → 约 18 小时
        assertEquals("18.00", service.dailyExhaustionEtaHours());
    }
}
