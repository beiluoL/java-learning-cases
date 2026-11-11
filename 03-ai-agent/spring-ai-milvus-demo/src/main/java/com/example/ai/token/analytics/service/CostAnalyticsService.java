package com.example.ai.token.analytics.service;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import com.example.ai.common.Views;
import com.example.ai.config.AiProperties;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.analytics.mapper.CostAnalyticsMapper;
import com.example.ai.token.budget.TokenBudgetService;
import com.example.ai.token.budget.entity.BudgetReservation;
import com.example.ai.token.budget.mapper.BudgetReservationMapper;
import com.example.ai.token.cost.CostAlertService;
import com.example.ai.token.cost.dto.CostBreakdown;
import com.example.ai.token.usage.dto.UsageQuery;
import com.example.ai.token.usage.entity.TokenUsage;
import com.example.ai.token.usage.mapper.TokenUsageMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 成本分析服务：强制时间范围与分页上限，所有统计都走 Mapper SQL，禁止全表扫描。 */
@Service
public class CostAnalyticsService {

    private static final Set<String> ALLOWED_SORT = Set.of(
            "created_at DESC", "created_at ASC", "total_cost DESC", "total_cost ASC", "latency_ms DESC");

    private final CostAnalyticsMapper analyticsMapper;
    private final TokenUsageMapper usageMapper;
    private final BudgetReservationMapper reservationMapper;
    private final CostAlertService alertService;
    private final TokenBudgetService budgetService;
    private final UserIdentityProvider identityProvider;
    private final AiProperties properties;
    private final Clock clock;

    public CostAnalyticsService(CostAnalyticsMapper analyticsMapper, TokenUsageMapper usageMapper,
                                BudgetReservationMapper reservationMapper, CostAlertService alertService,
                                TokenBudgetService budgetService, UserIdentityProvider identityProvider,
                                AiProperties properties, Clock clock) {
        this.analyticsMapper = analyticsMapper;
        this.usageMapper = usageMapper;
        this.reservationMapper = reservationMapper;
        this.alertService = alertService;
        this.budgetService = budgetService;
        this.identityProvider = identityProvider;
        this.properties = properties;
        this.clock = clock;
    }

    public record Range(LocalDateTime from, LocalDateTime to) {
    }

    /** 默认最近 7 天；跨度上限 92 天，防止统计接口拖垮数据库。 */
    public Range range(String startTime, String endTime) {
        LocalDateTime to = endTime == null || endTime.isBlank()
                ? LocalDateTime.now(clock).plusMinutes(1) : parse(endTime);
        LocalDateTime from = startTime == null || startTime.isBlank()
                ? to.minusDays(7) : parse(startTime);
        if (!from.isBefore(to)) {
            throw BusinessException.invalid("startTime 必须早于 endTime");
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days > properties.getAnalytics().getMaxRangeDays()) {
            throw BusinessException.invalid("查询跨度不能超过 " + properties.getAnalytics().getMaxRangeDays() + " 天");
        }
        return new Range(from, to);
    }

    public Views.Overview overview(String startTime, String endTime) {
        Range r = range(startTime, endTime);
        Map<String, Object> m = analyticsMapper.overview(r.from(), r.to());
        BigDecimal total = Views.decimal(m.get("totalCost"));
        long count = Views.longOf(m.get("requestCount"));
        BigDecimal avg = count == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
        double budgetRate = budgetService.status(identityProvider.currentUserId()).stream()
                .mapToDouble(Views.BudgetStatus::usageRate).max().orElse(0d);
        return new Views.Overview(total.toPlainString(), Views.longOf(m.get("totalTokens")),
                Views.longOf(m.get("inputTokens")), Views.longOf(m.get("outputTokens")),
                Views.longOf(m.get("cachedInputTokens")), count, avg.toPlainString(),
                m.get("successRate") == null ? 0d : Views.decimal(m.get("successRate")).doubleValue(),
                budgetRate);
    }

    public List<Views.TrendPoint> trend(String startTime, String endTime, String granularity) {
        Range r = range(startTime, endTime);
        String g = granularity == null ? "DAY" : granularity.toUpperCase();
        if (!Set.of("HOUR", "DAY", "MONTH").contains(g)) {
            throw BusinessException.invalid("granularity 仅支持 HOUR / DAY / MONTH");
        }
        List<Views.TrendPoint> out = new ArrayList<>();
        for (Map<String, Object> m : analyticsMapper.trend(r.from(), r.to(), g)) {
            out.add(new Views.TrendPoint(String.valueOf(m.get("bucket")), Views.money(m.get("totalCost")),
                    Views.longOf(m.get("inputTokens")), Views.longOf(m.get("outputTokens")),
                    Views.longOf(m.get("requestCount"))));
        }
        return out;
    }

    public List<Views.ModelCost> models(String startTime, String endTime) {
        Range r = range(startTime, endTime);
        BigDecimal total = Views.decimal(analyticsMapper.overview(r.from(), r.to()).get("totalCost"));
        List<Views.ModelCost> out = new ArrayList<>();
        for (Map<String, Object> m : analyticsMapper.models(r.from(), r.to())) {
            BigDecimal cost = Views.decimal(m.get("totalCost"));
            long count = Views.longOf(m.get("requestCount"));
            out.add(new Views.ModelCost(String.valueOf(m.get("provider")), String.valueOf(m.get("modelName")),
                    count, Views.longOf(m.get("inputTokens")), Views.longOf(m.get("outputTokens")),
                    cost.toPlainString(),
                    count == 0 ? "0" : cost.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP).toPlainString(),
                    ratio(cost, total)));
        }
        return out;
    }

    public List<Views.SceneCost> scenes(String startTime, String endTime) {
        Range r = range(startTime, endTime);
        BigDecimal total = Views.decimal(analyticsMapper.overview(r.from(), r.to()).get("totalCost"));
        List<Views.SceneCost> out = new ArrayList<>();
        for (Map<String, Object> m : analyticsMapper.scenes(r.from(), r.to())) {
            BigDecimal cost = Views.decimal(m.get("totalCost"));
            out.add(new Views.SceneCost(String.valueOf(m.get("businessScene")),
                    Views.longOf(m.get("requestCount")), Views.longOf(m.get("totalTokens")),
                    cost.toPlainString(), ratio(cost, total)));
        }
        return out;
    }

    public List<Views.TopItem> topModels(String startTime, String endTime, int limit) {
        Range r = range(startTime, endTime);
        return toTop(analyticsMapper.topModels(r.from(), r.to(), bound(limit)));
    }

    public List<Views.TopItem> topUsers(String startTime, String endTime, int limit) {
        Range r = range(startTime, endTime);
        return toTop(analyticsMapper.topUsers(r.from(), r.to(), bound(limit)));
    }

    public List<TokenUsage> recentRequests(String startTime, String endTime, int limit) {
        Range r = range(startTime, endTime);
        UsageQuery q = new UsageQuery();
        q.setStartTime(r.from());
        q.setEndTime(r.to());
        q.setSize(bound(limit));
        q.setPage(1);
        return usageMapper.page(q, 0, q.getSize());
    }

    public List<Views.AlertItem> alerts(int limit) {
        List<Views.AlertItem> out = new ArrayList<>();
        alertService.recent(limit).forEach(a -> out.add(new Views.AlertItem(a.getId(), a.getAlertType(),
                a.getBudgetType(), a.getMessage(), a.getSeverity(),
                a.getCreatedAt() == null ? "" : a.getCreatedAt().atZone(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))));
        return out;
    }

    public PageResult<TokenUsage> usagePage(UsageQuery q, String startTime, String endTime) {
        Range r = range(startTime, endTime);
        q.setStartTime(r.from());
        q.setEndTime(r.to());
        q.setPage(Math.max(q.getPage(), 1));
        q.setSize(Math.min(Math.max(q.getSize(), 1), properties.getAnalytics().getMaxPageSize()));
        // 兼容前端契约里的 camelCase 逗号写法（createdAt,DESC），统一映射到 SQL 列名白名单
        Map<String, String> sortAliases = Map.of(
                "createdAt,DESC", "created_at DESC", "createdAt,ASC", "created_at ASC",
                "totalCost,DESC", "total_cost DESC", "totalCost,ASC", "total_cost ASC",
                "latencyMs,DESC", "latency_ms DESC");
        String sort = sortAliases.getOrDefault(q.getSort(), q.getSort());
        if (!ALLOWED_SORT.contains(sort)) {
            sort = "created_at DESC";
        }
        q.setSort(sort);
        long offset = (long) (q.getPage() - 1) * q.getSize();
        return new PageResult<>(usageMapper.page(q, offset, q.getSize()),
                usageMapper.count(q), q.getPage(), q.getSize());
    }

    /** 调用详情：账单 + 成本计算过程 + 价格版本 + 预扣结算状态，默认不含 Prompt。 */
    public Views.UsageDetail usageDetail(String requestId) {
        TokenUsage usage = usageMapper.findByRequestId(requestId);
        if (usage == null) {
            throw BusinessException.notFound("调用记录不存在: " + requestId);
        }
        CostBreakdown breakdown = new CostBreakdown(usage.getPricingVersion(), usage.getPricingId(),
                null, null, null,
                usage.getInputTokens() - usage.getCachedInputTokens(), usage.getCachedInputTokens(),
                usage.getOutputTokens(), usage.getInputCost(), usage.getCachedInputCost(),
                usage.getOutputCost(), usage.getTotalCost(), usage.getCurrency());
        Map<String, Object> reservation = new HashMap<>();
        BudgetReservation r = reservationMapper.findByRequestId(requestId);
        if (r != null) {
            reservation.put("status", r.getStatus());
            reservation.put("reservedAmount", r.getReservedAmount());
            reservation.put("settledAmount", r.getSettledAmount());
        }
        return new Views.UsageDetail(usage, breakdown, reservation);
    }

    private List<Views.TopItem> toTop(List<Map<String, Object>> rows) {
        List<Views.TopItem> out = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            out.add(new Views.TopItem(String.valueOf(m.get("name")), Views.money(m.get("value"))));
        }
        return out;
    }

    private double ratio(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? 0d : part.divide(total, 6, RoundingMode.HALF_UP).doubleValue();
    }

    private int bound(int limit) {
        return Math.min(Math.max(limit, 1), properties.getAnalytics().getMaxPageSize());
    }

    private LocalDateTime parse(String value) {
        try {
            return Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw BusinessException.invalid("时间格式应为 ISO 8601，例如 2026-11-01T00:00:00Z");
            }
        }
    }
}
