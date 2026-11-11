package com.example.ai.alert;

import com.example.ai.common.AiEnums;
import com.example.ai.common.Views;
import com.example.ai.budget.TokenBudgetService;
import com.example.ai.alert.entity.CostAlert;
import com.example.ai.protection.entity.CostProtectionRule;
import com.example.ai.alert.mapper.CostAlertMapper;
import com.example.ai.protection.mapper.CostProtectionRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预算阈值预警：越线要报、只报越过的档、同档不重复报、回落要自动恢复。
 */
class BudgetThresholdAlertServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T06:00:00Z"), ZoneOffset.UTC);

    private CostProtectionRuleMapper ruleMapper;
    private CostAlertMapper alertMapper;
    private CostAlertService alertService;
    private TokenBudgetService budgetService;
    private BudgetThresholdAlertService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ruleMapper = mock(CostProtectionRuleMapper.class);
        alertMapper = mock(CostAlertMapper.class);
        alertService = mock(CostAlertService.class);
        budgetService = mock(TokenBudgetService.class);
        ObjectProvider<TokenBudgetService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(budgetService);
        when(ruleMapper.listEnabled()).thenReturn(rules());
        when(alertMapper.listOpenByTypeAndTarget(anyString(), anyString(), anyString())).thenReturn(List.of());
        service = new BudgetThresholdAlertService(provider, ruleMapper, alertMapper, alertService, CLOCK);
    }

    @Test
    void raisesOnlyThresholdsThatWereActuallyCrossed() {
        stubUsage("72.00000000", "100.00000000", "0.00000000");

        List<BudgetThresholdAlertService.Hit> hits = service.evaluate();

        // 72% 只越过了 50% 与 70% 两档，85/95/100 一档都不许报
        assertEquals(2, hits.size());
        assertTrue(hits.stream().allMatch(BudgetThresholdAlertService.Hit::raised));
        verify(alertService).alert(eq(AiEnums.AlertType.BUDGET_THRESHOLD), eq(AiEnums.AlertLevel.WARNING),
                eq("BUDGET"), eq("demo-user"), anyString(), eq(new BigDecimal("0.50000000")),
                any(BigDecimal.class), isNull(), eq("50.00%"));
        verify(alertService).alert(eq(AiEnums.AlertType.BUDGET_THRESHOLD), eq(AiEnums.AlertLevel.WARNING),
                eq("BUDGET"), eq("demo-user"), anyString(), eq(new BigDecimal("0.70000000")),
                any(BigDecimal.class), isNull(), eq("70.00%"));
        verify(alertService, times(2)).alert(any(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any());
    }

    @Test
    void includesReservedAmountWhenJudgingThreshold() {
        // 已用 40 + 未结算预扣 30 = 70 占用，必须算到 70% 档，不能只看 40%
        stubUsage("40.00000000", "100.00000000", "30.00000000");

        List<BudgetThresholdAlertService.Hit> hits = service.evaluate();

        assertEquals(2, hits.size());
        assertEquals("70.00%", hits.get(1).threshold());
    }

    @Test
    void doesNotRaiseTwiceWhenThresholdAlreadyOpen() {
        stubUsage("90.00000000", "100.00000000", "0.00000000");
        CostAlert open = new CostAlert();
        open.setId(9L);
        open.setAlertLevel("HIGH");
        open.setThresholdValue(new BigDecimal("0.85000000"));
        when(alertMapper.listOpenByTypeAndTarget(anyString(), anyString(), anyString())).thenReturn(List.of(open));

        List<BudgetThresholdAlertService.Hit> hits = service.evaluate();

        // 85% 已存在未恢复告警 → 只新报 50/70，85 不再重复落库
        assertTrue(hits.stream().filter(BudgetThresholdAlertService.Hit::raised)
                .noneMatch(h -> "85.00%".equals(h.threshold())));
        verify(alertService, never()).alert(any(), any(), anyString(), anyString(), anyString(),
                eq(new BigDecimal("0.85000000")), any(), any(), anyString());
    }

    @Test
    void resolvesAlertWhenUsageFallsBackBelowThreshold() {
        stubUsage("20.00000000", "100.00000000", "0.00000000");
        CostAlert open = new CostAlert();
        open.setId(7L);
        open.setAlertLevel("HIGH");
        open.setThresholdValue(new BigDecimal("0.85000000"));
        when(alertMapper.listOpenByTypeAndTarget(anyString(), anyString(), anyString())).thenReturn(List.of(open));

        List<BudgetThresholdAlertService.Hit> hits = service.evaluate();

        verify(alertMapper).resolve(eq(7L), any(LocalDateTime.class));
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).recovered());
        assertEquals("85.00%", hits.get(0).threshold());
    }

    @Test
    void skipsScopeWithZeroLimitInsteadOfDividingByZero() {
        stubUsage("0.00000000", "0.00000000", "0.00000000");

        assertTrue(service.evaluate().isEmpty());
        verify(alertService, never()).alert(any(), any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any());
    }

    @Test
    void ignoresRulesThatAreNotUsageRateConditions() {
        CostProtectionRule other = rule("rule_other", 3, "0.50000000");
        other.setConditionType("AGENT_STEPS");
        when(ruleMapper.listEnabled()).thenReturn(List.of(other));
        stubUsage("99.00000000", "100.00000000", "0.00000000");

        assertTrue(service.evaluate().isEmpty());
    }

    private void stubUsage(String used, String limit, String reserved) {
        when(budgetService.status(anyString())).thenReturn(List.of(
                new Views.BudgetStatus("USER", "demo-user", limit, used, reserved, limit, 0.72d)));
    }

    private List<CostProtectionRule> rules() {
        List<CostProtectionRule> list = new ArrayList<>();
        list.add(rule("budget_usage_50", 2, "0.50000000"));
        list.add(rule("budget_usage_70", 2, "0.70000000"));
        list.add(rule("budget_usage_85", 3, "0.85000000"));
        list.add(rule("budget_usage_95", 4, "0.95000000"));
        list.add(rule("budget_usage_100", 5, "1.00000000"));
        return list;
    }

    private CostProtectionRule rule(String code, int level, String threshold) {
        CostProtectionRule r = new CostProtectionRule();
        r.setRuleCode(code);
        r.setRuleName(code);
        r.setProtectionLevel(level);
        r.setConditionType("BUDGET_USAGE_RATE");
        r.setThresholdValue(new BigDecimal(threshold));
        r.setAction(level >= 5 ? "BLOCK_REQUEST" : level >= 4 ? "DISABLE_AGENT"
                : level >= 3 ? "DOWNGRADE_MODEL" : "NOTIFY");
        r.setPriority(100 - level);
        r.setEnabled(1);
        return r;
    }
}
