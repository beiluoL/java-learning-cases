package com.example.ai.protection;

import com.example.ai.alert.CostAlertService;
import com.example.ai.budget.TokenBudgetService;
import com.example.ai.common.AiEnums;
import com.example.ai.common.Views;
import com.example.ai.config.AiProperties;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.protection.entity.CostProtectionLog;
import com.example.ai.protection.entity.CostProtectionRule;
import com.example.ai.protection.mapper.CostProtectionRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动保护引擎：等级判定与恢复必须能真正回到 NORMAL。
 *
 * <p>重点回归一个自锁缺陷：保护引擎停用时写的 PROTECTION_ACTION 告警，
 * 曾被它自己当成「存在未恢复 CRITICAL」的输入，导致管理员 resume 之后等级仍是 SEVERE。
 */
class CostProtectionEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T06:00:00Z"), ZoneOffset.UTC);
    private static final String EMERGENCY_KEY = new AiProperties().getProtection().getEmergencyKey();

    private CostProtectionRuleMapper ruleMapper;
    private CostAlertService alertService;
    private TokenBudgetService budgetService;
    private StringRedisTemplate redis;
    /** 有状态的 Redis 替身：紧急开关必须能真的开与关，否则测不出恢复语义。 */
    private final Map<String, String> redisStore = new HashMap<>();

    private CostProtectionEngine engine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ruleMapper = mock(CostProtectionRuleMapper.class);
        alertService = mock(CostAlertService.class);
        budgetService = mock(TokenBudgetService.class);
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        redisStore.clear();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenAnswer(inv -> redisStore.get(inv.getArgument(0, String.class)));
        doAnswer(inv -> {
            redisStore.put(inv.getArgument(0, String.class), inv.getArgument(1, String.class));
            return null;
        }).when(valueOps).set(anyString(), anyString());
        when(redis.delete(anyString())).thenAnswer(inv -> redisStore.remove(inv.getArgument(0, String.class)) != null);

        when(ruleMapper.listEnabled()).thenReturn(List.of());
        when(ruleMapper.findActiveEmergency()).thenReturn(null);

        UserIdentityProvider identity = mock(UserIdentityProvider.class);
        when(identity.currentUserId()).thenReturn("demo-user");
        // 注意：currentUserId() 可能为 null，这里必须用 any() 而不是 anyString()，否则桩不生效
        when(budgetService.status(any())).thenReturn(List.of(status(0.10d)));

        engine = new CostProtectionEngine(ruleMapper, alertService, budgetService, identity, redis,
                new AiProperties(), CLOCK);
    }

    @Test
    void openProtectionActionAlertDoesNotKeepEngineAtSevere() {
        // 关键回归：即使存在自己写的未恢复 CRITICAL 审计告警，等级也不该被顶到 SEVERE
        when(alertService.countOpenByLevelExcluding(eq(AiEnums.AlertLevel.CRITICAL),
                eq(AiEnums.AlertType.PROTECTION_ACTION.name()))).thenReturn(0L);

        assertEquals(AiEnums.ProtectionLevel.NORMAL, engine.currentLevel());
    }

    @Test
    void otherOpenCriticalAlertStillEscalatesToSevere() {
        when(alertService.countOpenByLevelExcluding(eq(AiEnums.AlertLevel.CRITICAL),
                eq(AiEnums.AlertType.PROTECTION_ACTION.name()))).thenReturn(2L);

        assertEquals(AiEnums.ProtectionLevel.SEVERE, engine.currentLevel());
        // SEVERE 下非 Agent 场景强制降级、Agent 场景直接停
        assertTrue(engine.evaluate("CHAT").forceLowCostModel());
        assertFalse(engine.evaluate("AGENT").allowed());
    }

    @Test
    void budgetThresholdRuleDrivesLevelAndAgentIsStoppedAtSevere() {
        CostProtectionRule r = new CostProtectionRule();
        r.setRuleCode("budget_usage_95");
        r.setProtectionLevel(4);
        r.setConditionType("BUDGET_USAGE_RATE");
        r.setThresholdValue(new BigDecimal("0.95000000"));
        r.setAction("DISABLE_AGENT");
        r.setPriority(20);
        r.setEnabled(1);
        when(ruleMapper.listEnabled()).thenReturn(List.of(r));
        when(budgetService.status(any())).thenReturn(List.of(status(0.96d)));

        assertEquals(AiEnums.ProtectionLevel.SEVERE, engine.currentLevel());
        assertFalse(engine.evaluate("AGENT").allowAgent());
    }

    @Test
    void emergencyStopBlocksThenResumeRestoresNormal() {
        engine.emergencyStop("admin", "演练：验证全局阻断");

        verify(ruleMapper).insertLog(any(CostProtectionLog.class));
        verify(alertService).alert(eq(AiEnums.AlertType.PROTECTION_ACTION), eq(AiEnums.AlertLevel.CRITICAL),
                eq("GLOBAL"), eq("__ALL__"), anyString(), any(), any(), any());
        assertEquals("1", redisStore.get(EMERGENCY_KEY));
        assertEquals(AiEnums.ProtectionLevel.BLOCKED, engine.currentLevel());
        assertFalse(engine.evaluate("CHAT").allowed());

        CostProtectionLog active = new CostProtectionLog();
        active.setId(3L);
        when(ruleMapper.findActiveEmergency()).thenReturn(active);

        engine.resume("admin", "演练结束");

        // 恢复后数据库里那条记录已被标记 restored，查询不再命中
        when(ruleMapper.findActiveEmergency()).thenReturn(null);

        verify(ruleMapper).restoreEmergency(eq(3L), any());
        // 停用时写下的 PROTECTION_ACTION 必须一并恢复，否则未恢复的 CRITICAL 会继续压制等级
        verify(alertService).resolveOpenOf(AiEnums.AlertType.PROTECTION_ACTION, "GLOBAL", "__ALL__");
        assertFalse(redisStore.containsKey(EMERGENCY_KEY));
        assertEquals(AiEnums.ProtectionLevel.NORMAL, engine.currentLevel());
        assertTrue(engine.evaluate("CHAT").allowed());
    }

    private Views.BudgetStatus status(double rate) {
        return new Views.BudgetStatus("GLOBAL", "__ALL__", "100.00", "10.00", "0.00", "90.00", rate);
    }
}
