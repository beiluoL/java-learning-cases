package com.example.ai.token.analytics.service;

import com.example.ai.common.AiEnums;
import com.example.ai.common.Views;
import com.example.ai.optimize.CostOptimizationService;
import com.example.ai.token.analytics.entity.DailyCostSummary;
import com.example.ai.token.cost.CostLedgerService;
import com.example.ai.token.cost.SpendForecastService;
import com.example.ai.token.infra.InfrastructureCostService;
import com.example.ai.token.analytics.mapper.CostAnalyticsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 成本治理聚合视图：把「明细账单 / 账务流水 / 基础设施分摊 / 优化收益 / 预测」拼成管理端需要的结构。
 *
 * <p>口径纪律：真实 API 成本（ACTUAL）与基础设施分摊（ALLOCATED）分别返回，
 * 绝不合并成一个「总成本」冒充真实账单；节省（SAVED）单独展示。
 */
@Service
public class CostGovernanceService {

    private final CostAnalyticsService analyticsService;
    private final CostLedgerService ledgerService;
    private final InfrastructureCostService infraService;
    private final CostOptimizationService optimizationService;
    private final SpendForecastService forecastService;
    private final DailySummaryService dailySummaryService;
    private final CostAnalyticsMapper analyticsMapper;

    public CostGovernanceService(CostAnalyticsService analyticsService, CostLedgerService ledgerService,
                                 InfrastructureCostService infraService, CostOptimizationService optimizationService,
                                 SpendForecastService forecastService, DailySummaryService dailySummaryService,
                                 CostAnalyticsMapper analyticsMapper) {
        this.analyticsService = analyticsService;
        this.ledgerService = ledgerService;
        this.infraService = infraService;
        this.optimizationService = optimizationService;
        this.forecastService = forecastService;
        this.dailySummaryService = dailySummaryService;
        this.analyticsMapper = analyticsMapper;
    }

    public Views.CostBreakdownView breakdown(String startTime, String endTime) {
        CostAnalyticsService.Range r = analyticsService.range(startTime, endTime);
        BigDecimal apiCost = ledgerService.sum(AiEnums.CostKind.ACTUAL, r.from(), r.to());
        BigDecimal allocated = ledgerService.sum(AiEnums.CostKind.ALLOCATED, r.from(), r.to());
        BigDecimal saved = ledgerService.sum(AiEnums.CostKind.SAVED, r.from(), r.to());

        List<Views.BreakdownItem> items = new ArrayList<>();
        for (Map<String, Object> row : ledgerService.breakdown(AiEnums.CostKind.ACTUAL, r.from(), r.to())) {
            items.add(new Views.BreakdownItem(String.valueOf(row.get("entryType")), "ACTUAL",
                    Views.longOf(row.get("entryCount")), Views.money(row.get("amount")),
                    ratio(Views.decimal(row.get("amount")), apiCost)));
        }
        for (Map<String, Object> row : ledgerService.breakdown(AiEnums.CostKind.ALLOCATED, r.from(), r.to())) {
            items.add(new Views.BreakdownItem(String.valueOf(row.get("entryType")), "ALLOCATED",
                    Views.longOf(row.get("entryCount")), Views.money(row.get("amount")),
                    ratio(Views.decimal(row.get("amount")), allocated)));
        }
        for (Map<String, Object> row : ledgerService.breakdown(AiEnums.CostKind.SAVED, r.from(), r.to())) {
            items.add(new Views.BreakdownItem(String.valueOf(row.get("entryType")), "SAVED",
                    Views.longOf(row.get("entryCount")), Views.money(row.get("amount")),
                    ratio(Views.decimal(row.get("amount")), saved)));
        }
        return new Views.CostBreakdownView(apiCost.toPlainString(), allocated.toPlainString(),
                saved.toPlainString(), items);
    }

    public List<Views.UserCost> users(String startTime, String endTime, int limit) {
        CostAnalyticsService.Range r = analyticsService.range(startTime, endTime);
        List<Map<String, Object>> rows = analyticsMapper.userCost(r.from(), r.to());
        BigDecimal total = rows.stream().map(m -> Views.decimal(m.get("totalCost")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Views.UserCost> out = new ArrayList<>();
        int max = Math.min(Math.max(limit, 1), 100);
        for (int i = 0; i < Math.min(rows.size(), max); i++) {
            Map<String, Object> m = rows.get(i);
            BigDecimal cost = Views.decimal(m.get("totalCost"));
            out.add(new Views.UserCost(String.valueOf(m.get("userId")), Views.longOf(m.get("requestCount")),
                    cost.toPlainString(), ratio(cost, total)));
        }
        return out;
    }

    /** Token 使用分析：总量 + 模型/场景分布 + 缓存命中率。 */
    public Map<String, Object> tokenStatistics(String startTime, String endTime) {
        CostAnalyticsService.Range r = analyticsService.range(startTime, endTime);
        Map<String, Object> overview = analyticsMapper.overview(r.from(), r.to());
        long input = Views.longOf(overview.get("inputTokens"));
        long cached = Views.longOf(overview.get("cachedInputTokens"));
        double cacheRate = input == 0 ? 0d : (double) cached / input;
        return Map.of(
                "overview", analyticsService.overview(startTime, endTime),
                "cacheHitRate", cacheRate,
                "models", analyticsService.models(startTime, endTime),
                "scenes", analyticsService.scenes(startTime, endTime),
                "hourly", analyticsMapper.hourlyTokenTrend(r.from(), r.to()),
                "cacheStats", optimizationService.summary(r.from(), r.to()).cache());
    }

    public List<DailyCostSummary> dailySummary(String dimension, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            to = LocalDate.now();
            from = to.minusDays(30);
        }
        return dailySummaryService.list(dimension, from, to);
    }

    public Map<String, Object> rebuildDailySummary(LocalDate day) {
        LocalDate target = day == null ? LocalDate.now() : day;
        return Map.of("date", target.toString(), "rows", dailySummaryService.rebuild(target));
    }

    public Map<String, Object> optimization() {
        LocalDateTime to = LocalDateTime.now();
        return Map.of(
                "summary", optimizationService.summary(to.minusDays(30), to.plusMinutes(1)),
                "strategies", optimizationService.strategies());
    }

    public Map<String, Object> forecast() {
        return Map.of(
                "monthly", forecastService.monthly(),
                "dailyExhaustionEtaHours", forecastService.dailyExhaustionEtaHours(),
                "infrastructureTotal", infraService.totalAmount(
                        LocalDate.now().withDayOfMonth(1), LocalDate.now()));
    }

    private double ratio(BigDecimal part, BigDecimal total) {
        return total == null || total.signum() == 0 ? 0d
                : part.divide(total, 6, RoundingMode.HALF_UP).doubleValue();
    }
}
