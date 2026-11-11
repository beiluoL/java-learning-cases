package com.example.ai.token.cost;

import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.Views;
import com.example.ai.config.AiProperties;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.budget.TokenBudgetService;
import com.example.ai.token.cost.entity.CostProtectionLog;
import com.example.ai.token.cost.entity.CostProtectionRule;
import com.example.ai.token.cost.mapper.CostProtectionRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自动成本保护引擎。
 *
 * <p>等级判定（取所有命中条件的最高等级）：
 * <ol>
 *   <li>紧急开关打开 → 5（BLOCKED）；</li>
 *   <li>预算使用率命中规则阈值（默认 50/70/85/95/100）→ 对应等级；</li>
 *   <li>存在未恢复的 CRITICAL 告警 → 至少 4（SEVERE）。</li>
 * </ol>
 *
 * <p>保护等级只影响「是否放行 / 是否强制降级 / 是否允许 Agent」，
 * <b>不会</b>绕过用户预算：降级后的模型仍需重新走预算预扣。
 */
@Service
public class CostProtectionEngine {

    private static final Logger log = LoggerFactory.getLogger(CostProtectionEngine.class);

    private final CostProtectionRuleMapper ruleMapper;
    private final CostAlertService alertService;
    private final TokenBudgetService budgetService;
    private final UserIdentityProvider identityProvider;
    private final StringRedisTemplate redis;
    private final AiProperties properties;
    private final Clock clock;

    public CostProtectionEngine(CostProtectionRuleMapper ruleMapper, CostAlertService alertService,
                                TokenBudgetService budgetService, UserIdentityProvider identityProvider,
                                StringRedisTemplate redis, AiProperties properties, Clock clock) {
        this.ruleMapper = ruleMapper;
        this.alertService = alertService;
        this.budgetService = budgetService;
        this.identityProvider = identityProvider;
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /** 放行决策：调用方必须按此结果执行，不允许自行放宽。 */
    public record Decision(boolean allowed, AiEnums.ProtectionLevel level, boolean forceLowCostModel,
                           boolean allowAgent, boolean allowRagRewrite, String reason) {
    }

    public AiEnums.ProtectionLevel currentLevel() {
        if (!properties.getProtection().isEnabled()) {
            return AiEnums.ProtectionLevel.NORMAL;
        }
        if (isEmergencyStopped()) {
            return AiEnums.ProtectionLevel.BLOCKED;
        }
        double maxRate = maxBudgetUsageRate();
        int level = AiEnums.ProtectionLevel.NORMAL.code();
        String hit = null;
        List<CostProtectionRule> rules = new ArrayList<>(ruleMapper.listEnabled());
        rules.sort(Comparator.comparingInt(r -> r.getPriority() == null ? 100 : r.getPriority()));
        for (CostProtectionRule rule : rules) {
            if (!"BUDGET_USAGE_RATE".equals(rule.getConditionType()) || rule.getThresholdValue() == null) {
                continue;
            }
            if (BigDecimal.valueOf(maxRate).compareTo(rule.getThresholdValue()) >= 0) {
                level = Math.max(level, rule.getProtectionLevel() == null ? 1 : rule.getProtectionLevel());
                hit = rule.getRuleCode();
            }
        }
        if (properties.getProtection().isConsiderOpenAlerts()
                && alertService.countOpenByLevelExcluding(AiEnums.AlertLevel.CRITICAL,
                        AiEnums.AlertType.PROTECTION_ACTION.name()) > 0) {
            level = Math.max(level, AiEnums.ProtectionLevel.SEVERE.code());
            hit = hit == null ? "open-critical-alert" : hit + "+open-critical-alert";
        }
        if (hit != null) {
            log.debug("当前保护等级={} 命中规则={} 最高预算使用率={}", level, hit, maxRate);
        }
        return AiEnums.ProtectionLevel.ofCode(level);
    }

    public Decision evaluate(String scene) {
        AiEnums.ProtectionLevel level = currentLevel();
        boolean isAgent = AiEnums.BusinessScene.AGENT.name().equalsIgnoreCase(scene);
        return switch (level) {
            case BLOCKED -> new Decision(false, level, true, false, false,
                    "保护等级 5：预算阻断或紧急开关生效，拒绝新的付费调用");
            case SEVERE -> new Decision(!isAgent, level, true, false, false,
                    isAgent ? "保护等级 4：暂停高成本 Agent" : "保护等级 4：强制低成本模型并关闭 Query Rewrite");
            case LIMIT -> new Decision(true, level, true, false, true,
                    "保护等级 3：优先低成本模型");
            case WARNING -> new Decision(true, level, false, true, true,
                    "保护等级 2：成本警告，仅记录与通知");
            default -> new Decision(true, level, false, true, true, "保护等级 1：正常");
        };
    }

    public boolean isEmergencyStopped() {
        try {
            String v = redis.opsForValue().get(properties.getProtection().getEmergencyKey());
            if ("1".equals(v)) {
                return true;
            }
        } catch (DataAccessException e) {
            // Redis 不可用时以 DB 审计记录为准，避免误判为正常运行
            log.warn("读取紧急开关失败，回退数据库状态: {}", e.getClass().getSimpleName());
        }
        return ruleMapper.findActiveEmergency() != null;
    }

    /** 紧急停用：必须记录操作人与原因，可审计、可恢复。 */
    public void emergencyStop(String operator, String reason) {
        if (reason == null || reason.isBlank()) {
            throw BusinessException.invalid("紧急停用必须填写原因");
        }
        if (ruleMapper.findActiveEmergency() != null) {
            throw BusinessException.invalid("已存在生效中的紧急停用，请先恢复");
        }
        setEmergencyKey(true);
        CostProtectionLog l = log(AiEnums.ProtectionLevel.NORMAL.code(),
                AiEnums.ProtectionLevel.BLOCKED.code(), "GLOBAL", "__ALL__",
                operator, reason, "全局停用付费 AI 调用");
        ruleMapper.insertLog(l);
        alertService.alert(AiEnums.AlertType.PROTECTION_ACTION, AiEnums.AlertLevel.CRITICAL, "GLOBAL", "__ALL__",
                "管理员触发紧急停用：" + reason, null, null, null);
    }

    public void resume(String operator, String reason) {
        CostProtectionLog active = ruleMapper.findActiveEmergency();
        if (active == null) {
            throw BusinessException.invalid("当前没有生效中的紧急停用");
        }
        setEmergencyKey(false);
        ruleMapper.restoreEmergency(active.getId(), LocalDateTime.now(clock));
        ruleMapper.insertLog(log(AiEnums.ProtectionLevel.BLOCKED.code(), AiEnums.ProtectionLevel.NORMAL.code(),
                "GLOBAL", "__ALL__", operator,
                reason == null || reason.isBlank() ? "管理员恢复运行" : reason, "恢复付费 AI 调用"));
        // 事件已结束：把停用时自己写下的 PROTECTION_ACTION 告警一并恢复，
        // 否则它作为未恢复的 CRITICAL 会继续参与等级判定，把系统永久按在保护状态里
        alertService.resolveOpenOf(AiEnums.AlertType.PROTECTION_ACTION, "GLOBAL", "__ALL__");
    }

    public List<CostProtectionRule> rules() {
        return ruleMapper.list();
    }

    public CostProtectionRule updateRule(String ruleCode, Integer level, BigDecimal threshold,
                                         String action, Integer priority, Integer enabled, String remark) {
        CostProtectionRule rule = ruleMapper.findByCode(ruleCode);
        if (rule == null) {
            throw BusinessException.notFound("保护规则不存在: " + ruleCode);
        }
        if (level != null) {
            if (level < 1 || level > 5) {
                throw BusinessException.invalid("protectionLevel 必须在 1~5 之间");
            }
            rule.setProtectionLevel(level);
        }
        if (threshold != null) {
            if (threshold.signum() < 0) {
                throw BusinessException.invalid("thresholdValue 不能为负数");
            }
            rule.setThresholdValue(threshold);
        }
        if (action != null) {
            rule.setAction(action);
        }
        if (priority != null) {
            rule.setPriority(priority);
        }
        if (enabled != null) {
            rule.setEnabled(enabled);
        }
        if (remark != null) {
            rule.setRemark(remark);
        }
        ruleMapper.update(rule);
        return ruleMapper.findByCode(ruleCode);
    }

    public List<CostProtectionLog> logs(int limit) {
        return ruleMapper.listLogs(Math.min(Math.max(limit, 1), 100));
    }

    /** 当前各预算作用域的最高使用率，作为保护等级判定输入。 */
    public double maxBudgetUsageRate() {
        List<Views.BudgetStatus> statuses = budgetService.status(identityProvider.currentUserId());
        return statuses.stream().mapToDouble(Views.BudgetStatus::usageRate).max().orElse(0d);
    }

    private void setEmergencyKey(boolean on) {
        try {
            if (on) {
                redis.opsForValue().set(properties.getProtection().getEmergencyKey(), "1");
            } else {
                redis.delete(properties.getProtection().getEmergencyKey());
            }
        } catch (DataAccessException e) {
            log.error("写入紧急开关 Redis key 失败，已由数据库记录兜底: {}", e.getClass().getSimpleName());
        }
    }

    private CostProtectionLog log(int from, int to, String targetType, String targetId,
                                  String operator, String reason, String detail) {
        CostProtectionLog l = new CostProtectionLog();
        l.setAction(from == 1 && to == 5 ? "EMERGENCY_STOP" : "RESUME");
        l.setFromLevel(from);
        l.setToLevel(to);
        l.setTargetType(targetType);
        l.setTargetId(targetId);
        l.setOperator(operator == null || operator.isBlank() ? "admin" : operator);
        l.setReason(reason);
        l.setDetail(detail);
        return l;
    }
}
