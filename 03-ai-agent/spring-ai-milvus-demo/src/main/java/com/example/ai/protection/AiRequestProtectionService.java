package com.example.ai.protection;

import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.ErrorCode;
import com.example.ai.config.AiProperties;
import com.example.ai.alert.CostAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * AI 请求防护：用户级限流 + 全局/模型级并发控制。
 *
 * <p>为什么用 Redis 而不是本地计数器：多实例部署时本地计数各自为政，
 * 限流会随实例数线性失效。这里统一用 Redis 计数，key 带 {guard} hashtag。
 *
 * <p>失败策略：限流在 Redis 异常时<b>放行</b>（fail-open），
 * 因为真正的钱闸门是预算预扣（Redis 异常时 fail-closed 拒绝调用）。
 * 两级保护职责不同，不重复兜底也不互相替代。
 */
@Service
public class AiRequestProtectionService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestProtectionService.class);
    private static final String P = "ai:{guard}:";

    private final StringRedisTemplate redis;
    private final AiProperties properties;
    private final CostAlertService alertService;
    private final Clock clock;

    public AiRequestProtectionService(StringRedisTemplate redis, AiProperties properties,
                                      CostAlertService alertService, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.alertService = alertService;
        this.clock = clock;
    }

    /** 一次调用占用的并发槽位，必须在 finally 中 release，避免计数泄漏。 */
    public record Permit(String model) {
    }

    public Permit acquire(String userId, String model) {
        if (!properties.getRateLimit().isEnabled()) {
            return new Permit(model);
        }
        try {
            checkWindow("user:" + userId + ":min", properties.getRateLimit().getPerUserPerMinute(), 60, userId);
            checkWindow("user:" + userId + ":hour", properties.getRateLimit().getPerUserPerHour(), 3600, userId);
            checkWindow("user:" + userId + ":day:" + LocalDate.now(clock),
                    properties.getRateLimit().getPerUserPerDay(), 86400, userId);

            acquireConcurrency("global", properties.getRateLimit().getGlobalConcurrency(), userId);
            try {
                acquireConcurrency("model:" + model, properties.getRateLimit().getPerModelConcurrency(), userId);
            } catch (RuntimeException e) {
                // 模型槽位获取失败必须立刻归还已占用的全局槽位，否则并发计数会泄漏
                decr(P + "conc:global");
                throw e;
            }
            return new Permit(model);
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("限流检查 Redis 异常，放行由预算闸门兜底: {}", e.getClass().getSimpleName());
            return new Permit(model);
        }
    }

    public void release(Permit permit, String userId) {
        if (permit == null || !properties.getRateLimit().isEnabled()) {
            return;
        }
        try {
            decr(P + "conc:global");
            decr(P + "conc:model:" + permit.model());
        } catch (DataAccessException e) {
            log.warn("释放并发计数失败（将由 TTL 自动清理）: {}", e.getClass().getSimpleName());
        }
    }

    /** 周期内请求数统计，供预算策略中的 maxRequests 校验与 Dashboard 展示。 */
    public long requestsToday(String userId) {
        String v = redis.opsForValue().get(P + "user:" + userId + ":day:" + LocalDate.now(clock));
        return v == null ? 0L : Long.parseLong(v);
    }

    public long requestsThisMonth(String userId) {
        String key = P + "user:" + userId + ":month:" + YearMonth.now(clock);
        String v = redis.opsForValue().get(key);
        return v == null ? 0L : Long.parseLong(v);
    }

    private void checkWindow(String suffix, int limit, int ttlSeconds, String userId) {
        if (limit <= 0) {
            return;
        }
        String key = P + suffix;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        if (count != null && count > limit) {
            alertService.alert(AiEnums.AlertType.RATE_LIMITED, AiEnums.AlertLevel.WARNING, "USER", userId,
                    "用户请求超出限流阈值：" + count + " > " + limit + "（窗口 " + suffix + "）",
                    java.math.BigDecimal.valueOf(limit), java.math.BigDecimal.valueOf(count), null);
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "请求过于频繁，已触发限流（" + suffix + "），请稍后重试");
        }
    }

    private void acquireConcurrency(String name, int limit, String userId) {
        if (limit <= 0) {
            return;
        }
        String key = P + "conc:" + name;
        Long current = redis.opsForValue().increment(key);
        if (current != null && current == 1L) {
            // 兜底 TTL：进程崩溃不会让并发槽位永久占用
            redis.expire(key, Duration.ofSeconds(120));
        }
        if (current != null && current > limit) {
            decr(key);
            alertService.alert(AiEnums.AlertType.RATE_LIMITED, AiEnums.AlertLevel.HIGH, "GLOBAL", name,
                    "并发超出上限：" + current + " > " + limit, java.math.BigDecimal.valueOf(limit),
                    java.math.BigDecimal.valueOf(current), null);
            throw new BusinessException(ErrorCode.RATE_LIMITED, "当前并发过高（" + name + "），请稍后重试");
        }
    }

    private void decr(String key) {
        Long v = redis.opsForValue().decrement(key);
        if (v != null && v < 0) {
            redis.opsForValue().set(key, "0");
        }
    }
}
