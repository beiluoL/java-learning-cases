package com.example.ai.token.budget;

import com.example.ai.common.AiEnums;
import com.example.ai.common.MoneyScale;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.cost.CostAlertService;
import com.example.ai.token.usage.mapper.TokenUsageMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 定时兜底任务：
 * 1) 清理过期预扣（不能只靠 Key TTL）；
 * 2) 对账 Redis 预算消耗与 MySQL 账单，差异写 RECONCILE_DIFF 告警。
 * Redis 与 MySQL 不是同一事务，本任务是最终一致性的补偿机制。
 */
@Component
public class BudgetReconciler {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final TokenBudgetService budgetService;
    private final StringRedisTemplate redis;
    private final TokenUsageMapper usageMapper;
    private final CostAlertService alertService;
    private final UserIdentityProvider identityProvider;
    private final Clock clock;

    public BudgetReconciler(TokenBudgetService budgetService, StringRedisTemplate redis,
                            TokenUsageMapper usageMapper, CostAlertService alertService,
                            UserIdentityProvider identityProvider, Clock clock) {
        this.budgetService = budgetService;
        this.redis = redis;
        this.usageMapper = usageMapper;
        this.alertService = alertService;
        this.identityProvider = identityProvider;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void cleanup() {
        budgetService.cleanupExpired();
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 300000)
    public void reconcile() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        String userId = identityProvider.currentUserId();
        String usedKey = "ai:{budget}:used:DAILY:" + userId + ":" + today;
        String v = redis.opsForValue().get(usedKey);
        BigDecimal redisUsed = MoneyScale.fromUnits(v == null ? 0L : Long.parseLong(v));
        BigDecimal dbUsed = usageMapper.sumAllCost(userId, from, to);
        BigDecimal diff = redisUsed.subtract(dbUsed).abs();
        if (diff.compareTo(TOLERANCE) > 0) {
            alertService.alert(AiEnums.AlertType.RECONCILE_DIFF, "DAILY", userId,
                    "当日预算对账差异: Redis=" + redisUsed + " USD, MySQL=" + dbUsed + " USD, 差额=" + diff,
                    diff, "ERROR", null);
        }
    }
}
