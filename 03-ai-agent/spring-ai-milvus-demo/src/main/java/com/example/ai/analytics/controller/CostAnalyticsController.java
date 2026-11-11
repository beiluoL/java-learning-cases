package com.example.ai.analytics.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.PageResult;
import com.example.ai.common.Views;
import com.example.ai.analytics.service.CostAnalyticsService;
import com.example.ai.token.dto.UsageQuery;
import com.example.ai.token.entity.TokenUsage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** 成本分析 REST 接口。全部受 AdminAccessInterceptor 保护，参数校验与范围限制在 Service。 */
@RestController
@RequestMapping("/api/admin/ai")
public class CostAnalyticsController {

    private final CostAnalyticsService service;

    public CostAnalyticsController(CostAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/cost/overview")
    public ApiResponse<Views.Overview> overview(@RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(service.overview(startTime, endTime));
    }

    @GetMapping("/cost/trend")
    public ApiResponse<List<Views.TrendPoint>> trend(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime,
                                                     @RequestParam(defaultValue = "DAY") String granularity) {
        return ApiResponse.ok(service.trend(startTime, endTime, granularity));
    }

    @GetMapping("/cost/models")
    public ApiResponse<List<Views.ModelCost>> models(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(service.models(startTime, endTime));
    }

    @GetMapping("/cost/scenes")
    public ApiResponse<List<Views.SceneCost>> scenes(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(service.scenes(startTime, endTime));
    }

    @GetMapping("/cost/top-models")
    public ApiResponse<List<Views.TopItem>> topModels(@RequestParam(required = false) String startTime,
                                                      @RequestParam(required = false) String endTime,
                                                      @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(service.topModels(startTime, endTime, limit));
    }

    @GetMapping("/cost/top-users")
    public ApiResponse<List<Views.TopItem>> topUsers(@RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime,
                                                     @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(service.topUsers(startTime, endTime, limit));
    }

    @GetMapping("/cost/recent-requests")
    public ApiResponse<List<TokenUsage>> recent(@RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime,
                                                @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(service.recentRequests(startTime, endTime, limit));
    }

    @GetMapping("/cost/alerts")
    public ApiResponse<List<Views.AlertItem>> alerts(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(service.alerts(limit));
    }

    @GetMapping("/usage/page")
    public ApiResponse<PageResult<TokenUsage>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String businessScene,
            @RequestParam(required = false) String requestStatus,
            @RequestParam(required = false) String usageStatus,
            @RequestParam(required = false) BigDecimal minCost,
            @RequestParam(required = false) BigDecimal maxCost,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        UsageQuery q = new UsageQuery();
        q.setPage(page);
        q.setSize(size);
        q.setUserId(userId);
        q.setModelName(modelName);
        q.setBusinessScene(businessScene);
        q.setRequestStatus(requestStatus);
        q.setUsageStatus(usageStatus);
        q.setMinCost(minCost);
        q.setMaxCost(maxCost);
        if (sort != null) {
            q.setSort(sort);
        }
        return ApiResponse.ok(service.usagePage(q, startTime, endTime));
    }

    @GetMapping("/usage/{requestId}")
    public ApiResponse<Views.UsageDetail> detail(@PathVariable String requestId) {
        return ApiResponse.ok(service.usageDetail(requestId));
    }
}
