package com.example.ai.token.budget;

import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.ErrorCode;
import com.example.ai.common.MoneyScale;
import com.example.ai.common.Views;
import com.example.ai.config.AiProperties;
import com.example.ai.metrics.AiMetrics;
import com.example.ai.token.budget.entity.BudgetPolicy;
import com.example.ai.token.budget.entity.BudgetReservation;
import com.example.ai.token.budget.entity.BudgetTransaction;
import com.example.ai.token.budget.mapper.BudgetPolicyMapper;
import com.example.ai.token.budget.mapper.BudgetReservationMapper;
import com.example.ai.token.budget.mapper.BudgetTransactionMapper;
import com.example.ai.token.cost.CostAlertService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis 预算治理核心：预扣 / 结算 / 释放 / 状态 / 过期清理全部通过 Lua 原子执行。
 *
 * <p>一致性口径：Redis 是并发控制的真源，MySQL 是审计流水与账单的持久层，二者<b>不是</b>同事务。
 * 因此采用「先 Redis 原子预扣 → 调用模型 → 幂等结算 → 幂等落库」的最终一致策略，
 * 由 BudgetReconciler 定期对账，绝不声称强一致。
 *
 * <p>所有 Redis key 带 {budget} hashtag，未来迁 Cluster 时 Lua 涉及的 key 仍在同槽。
 */
@Service
public class TokenBudgetService {

    private static final String P = "ai:{budget}:";
    private static final String INDEX_KEY = P + "expire-index";
    private static final String ALL = "__ALL__";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> reserveScript;
    private final DefaultRedisScript<List> settleScript;
    private final DefaultRedisScript<List> releaseScript;
    private final DefaultRedisScript<List> cleanupScript;
    private final BudgetPolicyMapper policyMapper;
    private final BudgetReservationMapper reservationMapper;
    private final BudgetTransactionMapper transactionMapper;
    private final CostAlertService alertService;
    private final AiMetrics metrics;
    private final AiProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TokenBudgetService(StringRedisTemplate redis,
                              @Qualifier("budgetReserveScript") DefaultRedisScript<List> reserveScript,
                              @Qualifier("budgetSettleScript") DefaultRedisScript<List> settleScript,
                              @Qualifier("budgetReleaseScript") DefaultRedisScript<List> releaseScript,
                              @Qualifier("budgetCleanupScript") DefaultRedisScript<List> cleanupScript,
                              BudgetPolicyMapper policyMapper,
                              BudgetReservationMapper reservationMapper,
                              BudgetTransactionMapper transactionMapper,
                              CostAlertService alertService,
                              AiMetrics metrics,
                              AiProperties properties,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.redis = redis;
        this.reserveScript = reserveScript;
        this.settleScript = settleScript;
        this.releaseScript = releaseScript;
        this.cleanupScript = cleanupScript;
        this.policyMapper = policyMapper;
        this.reservationMapper = reservationMapper;
        this.transactionMapper = transactionMapper;
        this.alertService = alertService;
        this.metrics = metrics;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 一个预算作用域，usedKey/reservedKey 已按日/月周期分键，跨天自然重置。 */
    public record Scope(String budgetType, String targetId, BigDecimal limit, String usedKey, String reservedKey) {
    }

    public record ReservationResult(boolean approved, String reason, BigDecimal reserved, List<Scope> scopes) {
    }

    public record SettleResult(boolean changed, String status, BigDecimal actual, BigDecimal reserved) {
    }

    /**
     * 计算本次请求适用的全部预算作用域；无策略时回落到配置的默认全局额度。
     *
     * <p>作用域说明：
     * <ul>
     *   <li>DAILY / SCENE 按自然日分键，跨天自动归零；</li>
     *   <li>MONTHLY / USER / MODEL / GLOBAL / KNOWLEDGE_BASE / AGENT 用固定 key，由策略额度约束；</li>
     *   <li>REQUEST 级上限不放进来（它是「单次成本上限」，由调用方在预估阶段拦截，
     *       放进累计型 key 会导致第一次调用后永久拒批）。</li>
     * </ul>
     */
    public List<Scope> applicableScopes(String userId, String modelName) {
        return applicableScopes(userId, modelName, null, null);
    }

    public List<Scope> applicableScopes(String userId, String modelName, String scene, String knowledgeBaseId) {
        List<Scope> scopes = new ArrayList<>();
        List<String> types = new ArrayList<>(List.of("DAILY", "MONTHLY", "USER", "MODEL", "GLOBAL"));
        if (scene != null && !scene.isBlank()) {
            types.add("SCENE");
        }
        if (knowledgeBaseId != null && !knowledgeBaseId.isBlank()) {
            types.add("KNOWLEDGE_BASE");
        }
        if (AiEnums.BusinessScene.AGENT.name().equalsIgnoreCase(scene)) {
            types.add("AGENT");
        }
        for (String type : types) {
            String target = switch (type) {
                case "GLOBAL" -> ALL;
                case "MODEL" -> modelName == null ? ALL : modelName;
                case "SCENE" -> scene;
                case "KNOWLEDGE_BASE" -> knowledgeBaseId;
                case "AGENT" -> ALL;
                default -> userId;
            };
            if (target == null || target.isBlank()) {
                continue;
            }
            BudgetPolicy policy = policyMapper.findScope(type, target);
            if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
                continue;
            }
            BigDecimal limit = limitOf(type, policy);
            if (limit != null && limit.signum() > 0) {
                scopes.add(scope(type, target, limit));
            }
        }
        if (scopes.isEmpty()) {
            scopes.add(scope("GLOBAL", ALL, properties.getBudget().getDefaultMonthlyLimit()));
        }
        return scopes;
    }

    /** 原子预扣：一个 Lua 内先检查所有作用域，再一次性扣减，杜绝并发超额与部分成功。 */
    public ReservationResult reserve(String requestId, String userId, String modelName, BigDecimal estimated) {
        return reserve(requestId, userId, modelName, null, null, estimated);
    }

    public ReservationResult reserve(String requestId, String userId, String modelName, String scene,
                                     String knowledgeBaseId, BigDecimal estimated) {
        if (!properties.getBudget().isEnabled()) {
            return new ReservationResult(true, "DISABLED", BigDecimal.ZERO, List.of());
        }
        List<Scope> scopes = applicableScopes(userId, modelName, scene, knowledgeBaseId);
        long amount = MoneyScale.toUnits(estimated);
        long now = Instant.now(clock).getEpochSecond();
        int ttl = properties.getBudget().getReservationTtlSeconds();

        List<String> keys = new ArrayList<>();
        keys.add(P + "resv:" + requestId);
        keys.add(INDEX_KEY);
        for (Scope s : scopes) {
            keys.add(s.usedKey());
            keys.add(s.reservedKey());
        }
        List<String> args = new ArrayList<>(List.of(
                requestId, String.valueOf(amount), String.valueOf(now), String.valueOf(ttl),
                userId, String.valueOf(scopes.size())));
        for (Scope s : scopes) {
            args.add(String.valueOf(MoneyScale.toUnits(s.limit())));
            args.add(s.budgetType());
            args.add(s.targetId());
        }

        try {
            List<Object> result = redis.execute(reserveScript, keys, args.toArray());
            int code = code(result);
            String status = text(result, 1);
            if (code == 0) {
                if ("INSUFFICIENT".equals(status)) {
                    metrics.rejected();
                    alertService.alert(AiEnums.AlertType.BUDGET_EXCEEDED,
                            text(result, 2), null,
                            "预算不足，拒绝调用；作用域=" + text(result, 2) + "，剩余额度=" + text(result, 3),
                            estimated, "WARN", requestId);
                    return new ReservationResult(false, "预算不足（" + text(result, 2) + "）", estimated, scopes);
                }
                return new ReservationResult(false, "重复请求，幂等拒绝（" + status + "）", estimated, scopes);
            }
            persistReservation(requestId, userId, scopes.get(0), estimated, ttl);
            recordTx(requestId, userId, scopes, AiEnums.BudgetTxType.RESERVE, estimated, "预扣预算");
            return new ReservationResult(true, "RESERVED", estimated, scopes);
        } catch (DataAccessException e) {
            // 安全失败：付费模型未经预算检查不允许继续调用
            metrics.redisFailure();
            alertService.alert(AiEnums.AlertType.REDIS_FAILURE, null, null,
                    "Redis 预算操作失败，按安全失败策略拒绝请求: " + e.getClass().getSimpleName(),
                    estimated, "ERROR", requestId);
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE, "预算服务不可用，已安全拒绝本次调用");
        }
    }

    /** 幂等结算：释放预扣、按真实成本计入 used。unknown=true 表示供应商可能已收费但响应丢失。 */
    public SettleResult settle(String requestId, BigDecimal actual, boolean unknown) {
        List<Scope> scopes = scopesFromRedis(requestId);
        if (scopes.isEmpty()) {
            return new SettleResult(false, "NOT_FOUND", actual, BigDecimal.ZERO);
        }
        String status = unknown ? AiEnums.ReservationStatus.UNKNOWN.name() : AiEnums.ReservationStatus.SETTLED.name();
        List<String> args = List.of(requestId, String.valueOf(MoneyScale.toUnits(actual)), status,
                String.valueOf(Instant.now(clock).getEpochSecond()), String.valueOf(scopes.size()));
        try {
            List<Object> result = redis.execute(settleScript, buildKeys(requestId, scopes), args.toArray());
            boolean changed = code(result) == 1;
            BudgetReservation reservation = reservationMapper.findByRequestId(requestId);
            BigDecimal reserved = reservation == null || reservation.getReservedAmount() == null
                    ? BigDecimal.ZERO : reservation.getReservedAmount();
            if (changed) {
                reservationMapper.settleOrRelease(requestId, status, actual);
                recordTx(requestId, null, scopes, AiEnums.BudgetTxType.SETTLE, actual, "按实际成本结算");
                if (actual.compareTo(reserved) > 0) {
                    alertService.alert(AiEnums.AlertType.SETTLE_OVERRUN, null, null,
                            "实际成本 " + actual + " 高于预扣 " + reserved + "，已按实际计入并标记需复核",
                            actual.subtract(reserved), "ERROR", requestId);
                }
                publishSettled(requestId, reservation == null ? null : reservation.getUserId(), actual);
            }
            return new SettleResult(changed, changed ? status : text(result, 1), actual, reserved);
        } catch (DataAccessException e) {
            metrics.redisFailure();
            alertService.alert(AiEnums.AlertType.REDIS_FAILURE, null, null,
                    "结算时 Redis 异常，需人工对账: " + e.getClass().getSimpleName(), actual, "ERROR", requestId);
            return new SettleResult(false, "REDIS_FAILURE", actual, reservedFromDb(requestId));
        }
    }

    /** 幂等释放：调用失败/被拒绝时归还全部预扣额度。 */
    public void release(String requestId) {
        List<Scope> scopes = scopesFromRedis(requestId);
        if (scopes.isEmpty()) {
            return;
        }
        List<String> args = List.of(requestId, AiEnums.ReservationStatus.RELEASED.name(),
                String.valueOf(Instant.now(clock).getEpochSecond()), String.valueOf(scopes.size()));
        try {
            List<Object> result = redis.execute(releaseScript, buildKeys(requestId, scopes), args.toArray());
            if (code(result) == 1) {
                reservationMapper.settleOrRelease(requestId, AiEnums.ReservationStatus.RELEASED.name(), BigDecimal.ZERO);
                recordTx(requestId, null, scopes, AiEnums.BudgetTxType.RELEASE, BigDecimal.ZERO, "释放预扣");
            }
        } catch (DataAccessException e) {
            metrics.redisFailure();
            alertService.alert(AiEnums.AlertType.REDIS_FAILURE, null, null,
                    "释放预扣失败，等待过期清理任务兜底: " + e.getClass().getSimpleName(), null, "ERROR", requestId);
        }
    }

    /** 实时预算状态（含未结算的预扣额），供 Dashboard 进度条使用。 */
    public List<Views.BudgetStatus> status(String userId) {
        List<Views.BudgetStatus> out = new ArrayList<>();
        for (BudgetPolicy p : policyMapper.list()) {
            BigDecimal limit = limitOf(p.getBudgetType(), p);
            if (limit == null) {
                continue;
            }
            Scope s = scope(p.getBudgetType(), p.getTargetId(), limit);
            BigDecimal used = MoneyScale.fromUnits(readLong(s.usedKey()));
            BigDecimal held = MoneyScale.fromUnits(readLong(s.reservedKey()));
            BigDecimal committed = used.add(held);
            BigDecimal remaining = limit.subtract(committed).max(BigDecimal.ZERO);
            double rate = limit.signum() == 0 ? 0d
                    : committed.divide(limit, 6, RoundingMode.HALF_UP).doubleValue();
            out.add(new Views.BudgetStatus(p.getBudgetType(), p.getTargetId(), limit.toPlainString(),
                    used.toPlainString(), held.toPlainString(), remaining.toPlainString(), Math.min(rate, 1d)));
        }
        return out;
    }

    /** 过期预扣清理：Lua 原子扣回 reserved 总额，并同步 MySQL 流水状态。 */
    public int cleanupExpired() {
        long now = Instant.now(clock).getEpochSecond();
        int cleaned = 0;
        try {
            List<Object> result = redis.execute(cleanupScript, List.of(INDEX_KEY),
                    String.valueOf(now), "200", AiEnums.ReservationStatus.EXPIRED.name());
            cleaned = code(result);
        } catch (DataAccessException e) {
            metrics.redisFailure();
            return 0;
        }
        for (BudgetReservation r : reservationMapper.findExpired(LocalDateTime.now(clock), 200)) {
            reservationMapper.settleOrRelease(r.getRequestId(), AiEnums.ReservationStatus.EXPIRED.name(), BigDecimal.ZERO);
            recordTx(r.getRequestId(), r.getUserId(),
                    List.of(new Scope(r.getBudgetType(), r.getTargetId(), null, P + "used:EXPIRED", P + "reserved:EXPIRED")),
                    AiEnums.BudgetTxType.EXPIRE, BigDecimal.ZERO, "预扣过期自动清理");
        }
        return cleaned;
    }

    private void persistReservation(String requestId, String userId, Scope primary, BigDecimal amount, int ttl) {
        BudgetReservation r = new BudgetReservation();
        r.setRequestId(requestId);
        r.setBudgetType(primary.budgetType());
        r.setTargetId(primary.targetId());
        r.setUserId(userId);
        r.setReservedAmount(amount);
        r.setStatus(AiEnums.ReservationStatus.RESERVED.name());
        r.setExpireAt(LocalDateTime.ofInstant(Instant.now(clock).plusSeconds(ttl), ZoneOffset.UTC));
        try {
            reservationMapper.insert(r);
        } catch (DuplicateKeyException ignored) {
            // 幂等：同一 requestId 已有流水时不重复写
        }
    }

    /**
     * 预算变更流水：每个作用域一条，type+requestId+scope 组成幂等流水号。
     * 余额字段留空——并发下快照值会误导，实时余额统一由 {@link #status(String)} 提供。
     */
    private void recordTx(String requestId, String userId, List<Scope> scopes,
                          AiEnums.BudgetTxType type, BigDecimal amount, String remark) {
        if (scopes == null || scopes.isEmpty()) {
            return;
        }
        try {
            for (Scope s : scopes) {
                BudgetTransaction tx = new BudgetTransaction();
                tx.setTxNo(type.name() + "-" + requestId + "-" + s.budgetType() + "-" + s.targetId());
                tx.setRequestId(requestId);
                tx.setBudgetType(s.budgetType());
                tx.setTargetId(s.targetId());
                tx.setUserId(userId);
                tx.setTxType(type.name());
                tx.setAmount(type == AiEnums.BudgetTxType.RELEASE || type == AiEnums.BudgetTxType.EXPIRE
                        ? amount.negate() : amount);
                tx.setCurrency("USD");
                tx.setRemark(remark);
                transactionMapper.insert(tx);
            }
        } catch (DuplicateKeyException ignored) {
            // 幂等：重复结算/释放不会重复记流水
        } catch (RuntimeException e) {
            // 流水失败不阻断预算主流程，但必须留痕
            alertService.alert(AiEnums.AlertType.RECONCILE_DIFF, null, null,
                    "预算流水写入失败（Redis 已变更），需人工核对: " + e.getClass().getSimpleName(),
                    amount, "ERROR", requestId);
        }
    }

    /** 从 Redis 预扣明细还原作用域与完整 key，避免策略变更导致结算错位。 */
    private List<Scope> scopesFromRedis(String requestId) {
        Map<Object, Object> h = redis.opsForHash().entries(P + "resv:" + requestId);
        if (h.isEmpty()) {
            return List.of();
        }
        int n = Integer.parseInt(String.valueOf(h.getOrDefault("scopes", "0")));
        List<Scope> scopes = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            scopes.add(new Scope(
                    String.valueOf(h.get("bt" + i)), String.valueOf(h.get("tid" + i)), null,
                    String.valueOf(h.get("uk" + i)), String.valueOf(h.get("rk" + i))));
        }
        return scopes;
    }

    private List<String> buildKeys(String requestId, List<Scope> scopes) {
        List<String> keys = new ArrayList<>();
        keys.add(P + "resv:" + requestId);
        keys.add(INDEX_KEY);
        for (Scope s : scopes) {
            keys.add(s.usedKey());
            keys.add(s.reservedKey());
        }
        return keys;
    }

    private BigDecimal reservedFromDb(String requestId) {
        BudgetReservation r = reservationMapper.findByRequestId(requestId);
        return r == null || r.getReservedAmount() == null ? BigDecimal.ZERO : r.getReservedAmount();
    }

    /**
     * 通知旁路监听器「这个请求的预算已结算」，供阈值预警重新算一次使用率。
     *
     * <p>刻意吞掉所有异常：预警属于观测能力，绝不允许因为告警写失败而让已经完成的结算回滚或报错。
     */
    private void publishSettled(String requestId, String userId, BigDecimal actual) {
        try {
            eventPublisher.publishEvent(new BudgetSettledEvent(requestId, userId, actual));
        } catch (RuntimeException ignored) {
            // 旁路失败不影响结算结果
        }
    }

    private Scope scope(String type, String target, BigDecimal limit) {
        String period = switch (type) {
            case "DAILY", "SCENE" -> LocalDate.now(clock).toString();
            case "MONTHLY" -> YearMonth.now(clock).toString();
            default -> "ALL";
        };
        String tail = ":" + type + ":" + target + ":" + period;
        return new Scope(type, target, limit, P + "used" + tail, P + "reserved" + tail);
    }

    private BigDecimal limitOf(String type, BudgetPolicy p) {
        return switch (type) {
            case "DAILY" -> p.getDailyLimit();
            case "SCENE" -> p.getDailyLimit() != null ? p.getDailyLimit() : p.getMonthlyLimit();
            case "REQUEST" -> p.getPerRequestLimit();
            default -> p.getMonthlyLimit() != null ? p.getMonthlyLimit() : p.getDailyLimit();
        };
    }

    private long readLong(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0L : Long.parseLong(v);
    }

    /** Lua 返回值经 StringRedisTemplate 反序列化后可能是 String 也可能是 Number，两种都兼容。 */
    private int code(List<Object> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        Object first = result.get(0);
        if (first instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(first));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String text(List<Object> result, int index) {
        return result == null || result.size() <= index ? "" : String.valueOf(result.get(index));
    }
}
