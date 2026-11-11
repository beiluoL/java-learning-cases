package com.example.ai.token.analytics.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.common.Views;
import com.example.ai.token.analytics.entity.DailyCostSummary;
import com.example.ai.token.analytics.service.CostAnalyticsService;
import com.example.ai.token.analytics.service.CostGovernanceService;
import com.example.ai.token.infra.InfrastructureCostService;
import com.example.ai.token.infra.entity.InfrastructureCost;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 成本治理管理接口（V2 契约路径）。
 *
 * <p>与旧版 /api/admin/ai/** 并行保留：旧路径服务于已交付的 6 个页面，
 * 新路径覆盖成本构成、优化中心、预测与基础设施，避免破坏既有前端。
 */
@RestController
@RequestMapping("/api/admin/cost")
public class CostGovernanceController {

    private final CostAnalyticsService analyticsService;
    private final CostGovernanceService governanceService;
    private final InfrastructureCostService infraService;

    public CostGovernanceController(CostAnalyticsService analyticsService,
                                    CostGovernanceService governanceService,
                                    InfrastructureCostService infraService) {
        this.analyticsService = analyticsService;
        this.governanceService = governanceService;
        this.infraService = infraService;
    }

    @GetMapping("/overview")
    public ApiResponse<Views.Overview> overview(@RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(analyticsService.overview(startTime, endTime));
    }

    @GetMapping("/trend")
    public ApiResponse<List<Views.TrendPoint>> trend(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime,
                                                     @RequestParam(defaultValue = "DAY") String granularity) {
        return ApiResponse.ok(analyticsService.trend(startTime, endTime, granularity));
    }

    @GetMapping("/breakdown")
    public ApiResponse<Views.CostBreakdownView> breakdown(@RequestParam(required = false) String startTime,
                                                          @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(governanceService.breakdown(startTime, endTime));
    }

    @GetMapping("/models")
    public ApiResponse<List<Views.ModelCost>> models(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(analyticsService.models(startTime, endTime));
    }

    @GetMapping("/scenes")
    public ApiResponse<List<Views.SceneCost>> scenes(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(analyticsService.scenes(startTime, endTime));
    }

    @GetMapping("/users")
    public ApiResponse<List<Views.UserCost>> users(@RequestParam(required = false) String startTime,
                                                   @RequestParam(required = false) String endTime,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(governanceService.users(startTime, endTime, limit));
    }

    @GetMapping("/optimization")
    public ApiResponse<Map<String, Object>> optimization() {
        return ApiResponse.ok(governanceService.optimization());
    }

    @GetMapping("/forecast")
    public ApiResponse<Map<String, Object>> forecast() {
        return ApiResponse.ok(governanceService.forecast());
    }

    @GetMapping("/daily-summary")
    public ApiResponse<List<DailyCostSummary>> dailySummary(@RequestParam(defaultValue = "ALL") String dimension,
                                                            @RequestParam(required = false) LocalDate from,
                                                            @RequestParam(required = false) LocalDate to) {
        return ApiResponse.ok(governanceService.dailySummary(dimension, from, to));
    }

    @PostMapping("/daily-summary/rebuild")
    public ApiResponse<Map<String, Object>> rebuild(@RequestParam(required = false) LocalDate day) {
        return ApiResponse.ok(governanceService.rebuildDailySummary(day));
    }

    @GetMapping("/infrastructure")
    public ApiResponse<List<InfrastructureCost>> infrastructure() {
        return ApiResponse.ok(infraService.list());
    }

    @PostMapping("/infrastructure")
    public ApiResponse<InfrastructureCost> createInfrastructure(@RequestBody InfrastructureCost cost) {
        return ApiResponse.ok(infraService.create(cost));
    }

    @PutMapping("/infrastructure/{id:[0-9]+}")
    public ApiResponse<InfrastructureCost> updateInfrastructure(@PathVariable Long id,
                                                                @RequestBody InfrastructureCost cost) {
        return ApiResponse.ok(infraService.update(id, cost));
    }

    /** 按请求量分摊周期成本；分摊结果记为 ALLOCATED，不与真实 API 账单混算。 */
    @PostMapping("/infrastructure/allocate")
    public ApiResponse<InfrastructureCostService.AllocationResult> allocate(
            @RequestParam LocalDate periodStart, @RequestParam LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null) {
            throw BusinessException.invalid("periodStart / periodEnd 必填");
        }
        return ApiResponse.ok(infraService.allocate(periodStart, periodEnd));
    }
}
