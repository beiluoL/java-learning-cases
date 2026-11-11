package com.example.ai.token.cost;

import com.example.ai.token.agent.mapper.AgentCostTraceMapper;
import com.example.ai.token.analytics.mapper.CostAnalyticsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异常检测规则：每条规则都要能在「达到条件时命中、未达到时静默」两端受控。
 *
 * <p>用桩数据而不是往业务表塞假行，避免污染真实账单；
 * 规则判定全部是「今日 vs 近 7 日基线」的相对比较，桩数据足以覆盖。
 */
class AnomalyDetectionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T06:00:00Z"), ZoneOffset.UTC);

    private CostAnalyticsMapper analyticsMapper;
    private AgentCostTraceMapper agentTraceMapper;
    private CostAlertService alertService;
    private AnomalyDetectionService service;

    @BeforeEach
    void setUp() {
        analyticsMapper = mock(CostAnalyticsMapper.class);
        agentTraceMapper = mock(AgentCostTraceMapper.class);
        alertService = mock(CostAlertService.class);

        when(analyticsMapper.userCost(any(), any())).thenReturn(List.of());
        when(analyticsMapper.tokenStats(any(), any())).thenReturn(Map.of());
        when(analyticsMapper.trend(any(), any(), anyString())).thenReturn(List.of());
        when(analyticsMapper.overview(any(), any())).thenReturn(overview(0, 0, 0, 1.0d));
        when(agentTraceMapper.countStopped(any(), any(), anyString())).thenReturn(0L);

        service = new AnomalyDetectionService(analyticsMapper, agentTraceMapper, alertService, CLOCK);
    }

    @Test
    void noFindingsWhenEverythingIsNormal() {
        assertTrue(service.scan().isEmpty());
        verify(alertService, never()).alert(any(), any(), anyString(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    void userCostSpikeNeedsThreeTimesBaselineAndAbsoluteFloor() {
        // 基线 7 天日均 1.00 → 阈值 3.00；今日 5.00 命中
        when(analyticsMapper.userCost(any(), any()))
                .thenReturn(List.of(row("u-spike", "7.00")))
                .thenReturn(List.of(row("u-spike", "5.00")));

        List<AnomalyDetectionService.Finding> findings = service.scan();

        assertEquals(1, findings.size());
        assertEquals("ANOMALY_USER_SPIKE", findings.get(0).rule());
        assertEquals("u-spike", findings.get(0).targetId());
        assertEquals("HIGH", findings.get(0).level());
    }

    @Test
    void smallAbsoluteAmountIsNotReportedAsSpike() {
        // 基线 0 → 阈值被 0.01 兜底；今日 0.005 不到下限，不该报
        when(analyticsMapper.userCost(any(), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(row("u-small", "0.005")));

        assertTrue(service.scan().isEmpty());
    }

    @Test
    void tokenSpikeComparesAgainstBaselineAverage() {
        when(analyticsMapper.tokenStats(any(), any()))
                .thenReturn(Map.of("maxTokens", 12000L, "avgTokens", 0L))
                .thenReturn(Map.of("maxTokens", 100L, "avgTokens", 200.00d));

        List<AnomalyDetectionService.Finding> findings = service.scan();

        assertEquals(1, findings.size());
        assertEquals("ANOMALY_TOKEN_SPIKE", findings.get(0).rule());
    }

    @Test
    void errorRateRuleRequiresEnoughSamples() {
        when(analyticsMapper.overview(any(), any())).thenReturn(overview(0, 0, 5, 0.2d));
        assertTrue(service.scan().isEmpty());

        when(analyticsMapper.overview(any(), any())).thenReturn(overview(0, 0, 20, 0.2d));
        List<AnomalyDetectionService.Finding> findings = service.scan();
        assertEquals(1, findings.size());
        assertEquals("ANOMALY_ERROR_RATE", findings.get(0).rule());
    }

    @Test
    void cacheHitDropNeedsBaselineAboveTwentyPercentAndVolume() {
        // overview 在 scan 内被调用三次：今日总览 → 今日命中率 → 基线命中率
        // 今日 20000 输入但只有 1000 命中 → 5%；基线 1000/1000 → 100%
        Map<String, Object> today = overview(20000, 1000, 0, 1.0d);
        Map<String, Object> baseline = overview(1000, 1000, 0, 1.0d);
        when(analyticsMapper.overview(any(), any()))
                .thenReturn(today)
                .thenReturn(today)
                .thenReturn(baseline);

        List<AnomalyDetectionService.Finding> findings = service.scan();

        assertEquals(1, findings.size());
        assertEquals("ANOMALY_CACHE_HIT_DROP", findings.get(0).rule());
    }

    @Test
    void nightCallsAreSummedAcrossZeroToSixHours() {
        when(analyticsMapper.trend(any(), any(), eq("HOUR"))).thenReturn(List.of(
                Map.of("bucket", "2026-11-11 01:00", "requestCount", 15L),
                Map.of("bucket", "2026-11-11 05:00", "requestCount", 10L),
                Map.of("bucket", "2026-11-11 09:00", "requestCount", 99L)));

        List<AnomalyDetectionService.Finding> findings = service.scan();

        assertEquals(1, findings.size());
        assertEquals("ANOMALY_NIGHT_CALL", findings.get(0).rule());
        // 只统计 0-6 点：15 + 10 = 25，白天的 99 次不计入
        assertEquals(0, new BigDecimal("25").compareTo(findings.get(0).currentValue()));
    }

    @Test
    void agentLoopIsCritical() {
        when(agentTraceMapper.countStopped(any(), any(), eq("LOOP_DETECTED"))).thenReturn(2L);

        List<AnomalyDetectionService.Finding> findings = service.scan();

        assertEquals(1, findings.size());
        assertEquals("ANOMALY_AGENT_LOOP", findings.get(0).rule());
        assertEquals("CRITICAL", findings.get(0).level());
    }

    @Test
    void findingsAreWrittenToAlertCenter() {
        when(agentTraceMapper.countStopped(any(), any(), eq("LOOP_DETECTED"))).thenReturn(1L);

        service.scan();

        verify(alertService).alert(any(), any(), eq("AGENT"), eq("__ALL__"), anyString(), any(), any(), any());
    }

    private Map<String, Object> overview(long input, long cached, long requests, double successRate) {
        Map<String, Object> m = new HashMap<>();
        m.put("inputTokens", input);
        m.put("cachedInputTokens", cached);
        m.put("requestCount", requests);
        m.put("successRate", BigDecimal.valueOf(successRate));
        m.put("totalCost", BigDecimal.ZERO);
        return m;
    }

    private Map<String, Object> row(String userId, String cost) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("totalCost", new BigDecimal(cost));
        return m;
    }
}
