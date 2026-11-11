package com.example.ai.alert;

import com.example.ai.common.AiEnums;
import com.example.ai.common.Views;
import com.example.ai.budget.BudgetSettledEvent;
import com.example.ai.budget.TokenBudgetService;
import com.example.ai.alert.entity.CostAlert;
import com.example.ai.protection.entity.CostProtectionRule;
import com.example.ai.alert.mapper.CostAlertMapper;
import com.example.ai.protection.mapper.CostProtectionRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 预算使用率阈值预警：把 {@code ai_cost_protection_rule} 里的 BUDGET_USAGE_RATE 规则
 * 变成真正会落库的告警，并负责「越线即报、回落即恢复」。
 *
 * <p>为什么单独一个类：{@link CostProtectionEngine} 只在请求进来时被动算等级，
 * 它从不写告警；而告警中心需要一份独立的、可定时对账的越线记录。
 * 两者共用同一份规则表，但职责不同——一个负责「拦」，一个负责「报」。
 *
 * <p>三个触发源：
 * <ol>
 *   <li>{@link BudgetSettledEvent}：每次结算后评估（带 5 秒去抖，避免高并发下反复全量扫）；</li>
 *   <li>定时兜底：每 2 分钟一次，覆盖「只配了额度没发生结算」与「额度被人工调小」；</li>
 *   <li>管理端手动触发（{@code POST /api/admin/cost-alert/evaluate}）。</li>
 * </ol>
 *
 * <p>恢复语义：同一作用域下，若某档位已不再越线且存在对应未恢复告警，则置为 RESOLVED。
 * 跨天/跨月额度归零后无需人工干预，下一次扫描会自动恢复。
 */
@Service
public class BudgetThresholdAlertService {

    private static final Logger log = LoggerFactory.getLogger(BudgetThresholdAlertService.class);
    private static final String TARGET_TYPE = "BUDGET";
    private static final String CONDITION = "BUDGET_USAGE_RATE";
    /** 事件驱动评估去抖窗口：高并发下避免每条结算都做一次全量扫描。 */
    private static final long EVENT_DEBOUNCE_MS = 5_000L;

    private final ObjectProvider<TokenBudgetService> budgetServiceProvider;
    private final CostProtectionRuleMapper ruleMapper;
    private final CostAlertMapper alertMapper;
    private final CostAlertService alertService;
    private final Clock clock;
    private final AtomicLong lastEventRun = new AtomicLong(0L);

    public BudgetThresholdAlertService(ObjectProvider<TokenBudgetService> budgetServiceProvider,
                                       CostProtectionRuleMapper ruleMapper,
                                       CostAlertMapper alertMapper,
                                       CostAlertService alertService,
                                       Clock clock) {
        this.budgetServiceProvider = budgetServiceProvider;
        this.ruleMapper = ruleMapper;
        this.alertMapper = alertMapper;
        this.alertService = alertService;
        this.clock = clock;
    }

    /** 单次评估结果，供接口回显「这次扫描看到了什么」。 */
    public record Hit(String budgetType, String targetId, String threshold, String usageRate,
                      String alertLevel, String action, boolean raised, boolean recovered) {
    }

    /** 结算后评估；失败只记日志，绝不影响已经完成的结算。 */
    @EventListener
    public void onBudgetSettled(BudgetSettledEvent event) {
        long now = System.currentTimeMillis();
        long prev = lastEventRun.get();
        if (now - prev < EVENT_DEBOUNCE_MS) {
            return;
        }
        if (!lastEventRun.compareAndSet(prev, now)) {
            return;
        }
        try {
            evaluate();
        } catch (RuntimeException e) {
            log.warn("结算后阈值预警评估失败 requestId={}: {}", event.requestId(), e.getClass().getSimpleName());
        }
    }

    /** 定时兜底扫描。 */
    @Scheduled(fixedDelay = 120_000L, initialDelay = 90_000L)
    public void scheduledSweep() {
        try {
            List<Hit> hits = evaluate();
            if (!hits.isEmpty()) {
                log.info("预算阈值扫描：{} 个档位状态变化", hits.size());
            }
        } catch (RuntimeException e) {
            log.warn("预算阈值扫描失败: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 全量评估一次：对每个预算作用域，把「已越线的档位」写成告警，把「已回落的档位」恢复掉。
     *
     * @return 仅返回产生了状态变化的档位（新报或新恢复），无变化时为空
     */
    public List<Hit> evaluate() {
        List<CostProtectionRule> rules = thresholdRules();
        if (rules.isEmpty()) {
            return List.of();
        }
        TokenBudgetService budgetService = budgetServiceProvider.getIfAvailable();
        if (budgetService == null) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>();
        for (Views.BudgetStatus status : budgetService.status("__ALL__")) {
            BigDecimal limit = Views.decimal(status.limitAmount());
            if (limit.signum() <= 0) {
                continue;
            }
            // 含未结算预扣：预扣额度已被占用，越线判断必须算进去，否则「已花完但还没结算」会漏报
            BigDecimal committed = Views.decimal(status.usedAmount()).add(Views.decimal(status.reservedAmount()));
            double rate = committed.divide(limit, 6, RoundingMode.HALF_UP).doubleValue();

            Map<BigDecimal, CostProtectionRule> crossed = new LinkedHashMap<>();
            for (CostProtectionRule rule : rules) {
                if (BigDecimal.valueOf(rate).compareTo(rule.getThresholdValue()) >= 0) {
                    crossed.put(rule.getThresholdValue(), rule);
                }
            }
            for (CostProtectionRule rule : crossed.values()) {
                boolean exists = openAlerts(status.targetId()).stream()
                        .anyMatch(a -> sameThreshold(a.getThresholdValue(), rule.getThresholdValue()));
                if (!exists) {
                    alertService.alert(AiEnums.AlertType.BUDGET_THRESHOLD, alertLevelOf(rule.getProtectionLevel()),
                            TARGET_TYPE, status.targetId(),
                            message(status, committed, limit, rate, rule),
                            rule.getThresholdValue(), committed, null,
                            thresholdDiscriminator(rule.getThresholdValue()));
                    hits.add(new Hit(status.budgetType(), status.targetId(), pct(rule.getThresholdValue()),
                            pct(rate), alertLevelOf(rule.getProtectionLevel()).name(), rule.getAction(),
                            true, false));
                }
            }
            // 回落恢复：已不再越线的档位，把对应未恢复告警关掉
            for (CostAlert alert : openAlerts(status.targetId())) {
                if (alert.getThresholdValue() == null) {
                    continue;
                }
                boolean stillCrossed = crossed.keySet().stream()
                        .anyMatch(t -> sameThreshold(t, alert.getThresholdValue()));
                if (!stillCrossed) {
                    alertMapper.resolve(alert.getId(), LocalDateTime.now(clock));
                    hits.add(new Hit(status.budgetType(), status.targetId(), pct(alert.getThresholdValue()),
                            pct(rate), alert.getAlertLevel(), "RECOVERED", false, true));
                }
            }
        }
        return hits;
    }

    /** 保护等级 → 告警等级：1 观察、2 提醒、3 高危、4 严重、5 阻断。 */
    private AiEnums.AlertLevel alertLevelOf(Integer protectionLevel) {
        int level = protectionLevel == null ? 1 : protectionLevel;
        return switch (level) {
            case 1 -> AiEnums.AlertLevel.INFO;
            case 2 -> AiEnums.AlertLevel.WARNING;
            case 3 -> AiEnums.AlertLevel.HIGH;
            case 4 -> AiEnums.AlertLevel.CRITICAL;
            default -> AiEnums.AlertLevel.BLOCKED;
        };
    }

    private String message(Views.BudgetStatus status, BigDecimal committed, BigDecimal limit,
                           double rate, CostProtectionRule rule) {
        return "预算使用率达到 " + pct(rate) + "，触发阈值 " + pct(rule.getThresholdValue())
                + "（规则 " + rule.getRuleCode() + "，动作 " + rule.getAction() + "）；作用域 "
                + status.budgetType() + "/" + status.targetId()
                + "，已占用 " + committed.setScale(8, RoundingMode.HALF_UP).toPlainString()
                + " / 额度 " + limit.setScale(8, RoundingMode.HALF_UP).toPlainString() + " USD（含未结算预扣）";
    }

    /** 判别位：同一作用域不同档位必须各成一条告警，否则 50% 与 70% 会被去重逻辑合并。 */
    private String thresholdDiscriminator(BigDecimal threshold) {
        return pct(threshold);
    }

    private List<CostProtectionRule> thresholdRules() {
        List<CostProtectionRule> all = new ArrayList<>(ruleMapper.listEnabled());
        all.removeIf(r -> !CONDITION.equals(r.getConditionType()) || r.getThresholdValue() == null);
        all.sort(Comparator.comparing(CostProtectionRule::getThresholdValue));
        return all;
    }

    private List<CostAlert> openAlerts(String targetId) {
        return alertMapper.listOpenByTypeAndTarget(AiEnums.AlertType.BUDGET_THRESHOLD.name(), TARGET_TYPE, targetId);
    }

    private boolean sameThreshold(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private String pct(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String pct(double rate) {
        return pct(BigDecimal.valueOf(rate));
    }
}
