package com.example.ai.alert;

import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import com.example.ai.alert.entity.CostAlert;
import com.example.ai.alert.mapper.CostAlertMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本告警中心：统一入库 + 指纹去重 + 确认/恢复流转。
 *
 * <p>去重策略：同一「告警类型 + 对象 + 等级」只保留一条未恢复告警，
 * 后续重复触发只累加 occurrence_count 并刷新 last_seen_at，
 * 避免同一异常每秒刷一条记录。
 */
@Service
public class CostAlertService {

    private static final Logger log = LoggerFactory.getLogger(CostAlertService.class);

    private final CostAlertMapper mapper;
    private final Clock clock;

    public CostAlertService(CostAlertMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 兼容旧调用：按 severity 推导等级，供既有预算/对账代码复用。 */
    public void alert(AiEnums.AlertType type, String budgetType, String targetId,
                      String message, BigDecimal amount, String severity, String requestId) {
        AiEnums.AlertLevel level = switch (severity == null ? "WARN" : severity) {
            case "INFO" -> AiEnums.AlertLevel.INFO;
            case "ERROR" -> AiEnums.AlertLevel.HIGH;
            default -> AiEnums.AlertLevel.WARNING;
        };
        alert(type, level, budgetType == null ? null : "BUDGET", targetId, message, null, amount, requestId);
    }

    /** 标准告警入口：带等级、对象、阈值与当前值，便于 Dashboard 解释「为什么报」。 */
    public void alert(AiEnums.AlertType type, AiEnums.AlertLevel level, String targetType, String targetId,
                      String message, BigDecimal threshold, BigDecimal current, String requestId) {
        alert(type, level, targetType, targetId, message, threshold, current, requestId, null);
    }

    /**
     * 带判别位的告警入口。
     *
     * <p>判别位用于「同类型、同对象、同等级，但业务上属于不同事件」的场景，
     * 最典型的是预算阈值预警：50% 与 70% 两条规则的 protection_level 都是 2，
     * 仅靠 type|targetType|targetId|level 会把它们合并成一条，丢掉档位区分。
     * 传 null 时指纹与旧签名完全一致，历史告警不会因升级而重复。
     */
    public void alert(AiEnums.AlertType type, AiEnums.AlertLevel level, String targetType, String targetId,
                      String message, BigDecimal threshold, BigDecimal current, String requestId,
                      String discriminator) {
        String fingerprint = type.name() + "|" + nullSafe(targetType) + "|" + nullSafe(targetId) + "|"
                + level.name() + (discriminator == null || discriminator.isBlank() ? "" : "|" + discriminator);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            CostAlert active = mapper.findActiveByFingerprint(fingerprint);
            if (active != null) {
                mapper.bumpOccurrence(active.getId(), now, level.name(), truncate(message));
                return;
            }
            CostAlert a = new CostAlert();
            a.setRequestId(requestId);
            a.setAlertType(type.name());
            a.setAlertLevel(level.name());
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            a.setMessage(truncate(message));
            a.setAmount(current);
            a.setThresholdValue(threshold);
            a.setCurrentValue(current);
            a.setSeverity(level == AiEnums.AlertLevel.INFO ? "INFO"
                    : level == AiEnums.AlertLevel.WARNING ? "WARN" : "ERROR");
            a.setStatus(AiEnums.AlertStatus.OPEN.name());
            a.setOccurrenceCount(1);
            a.setFingerprint(fingerprint);
            a.setLastSeenAt(now);
            mapper.insert(a);
        } catch (RuntimeException e) {
            // 告警失败不能反过来影响主业务
            log.error("写入成本告警失败 type={} target={}: {}", type, targetId, e.getClass().getSimpleName());
        }
    }

    public List<CostAlert> recent(int limit) {
        return mapper.listRecent(Math.min(Math.max(limit, 1), 100));
    }

    public PageResult<CostAlert> page(String alertType, String alertLevel, String status, String targetType,
                                      LocalDateTime from, LocalDateTime to, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long offset = (long) (p - 1) * s;
        return new PageResult<>(
                mapper.page(alertType, alertLevel, status, targetType, from, to, offset, s),
                mapper.count(alertType, alertLevel, status, targetType, from, to), p, s);
    }

    public CostAlert get(Long id) {
        CostAlert a = mapper.findById(id);
        if (a == null) {
            throw BusinessException.notFound("告警不存在: " + id);
        }
        return a;
    }

    public void acknowledge(Long id, String operator) {
        get(id);
        mapper.acknowledge(id, operator == null ? "admin" : operator, LocalDateTime.now(clock));
    }

    public void resolve(Long id) {
        get(id);
        mapper.resolve(id, LocalDateTime.now(clock));
    }

    public long countOpenByLevel(AiEnums.AlertLevel level) {
        return mapper.countOpenByLevel(level.name());
    }

    /** 未恢复的高等级告警数，排除指定类型（保护等级判定用，避免 PROTECTION_ACTION 自锁）。 */
    public long countOpenByLevelExcluding(AiEnums.AlertLevel level, String excludeType) {
        return mapper.countOpenByLevelExcluding(level.name(), excludeType);
    }

    /**
     * 恢复某个对象下指定类型的全部未恢复告警。
     *
     * <p>用于「事件已经结束」的场景，例如管理员恢复运行后，
     * 由紧急停用本身写下的 PROTECTION_ACTION 告警不应继续挂在预警中心里等着人点。
     *
     * @return 实际恢复条数
     */
    public int resolveOpenOf(AiEnums.AlertType type, String targetType, String targetId) {
        int resolved = 0;
        for (CostAlert a : mapper.listOpenByTypeAndTarget(type.name(), targetType, targetId)) {
            resolved += mapper.resolve(a.getId(), LocalDateTime.now(clock));
        }
        return resolved;
    }

    private String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
