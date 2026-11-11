package com.example.ai.analytics.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.PageResult;
import com.example.ai.common.Views;
import com.example.ai.analytics.service.CostAnalyticsService;
import com.example.ai.analytics.service.CostGovernanceService;
import com.example.ai.token.dto.UsageQuery;
import com.example.ai.token.entity.TokenUsage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Token 使用分析接口（V2 契约路径）。 */
@RestController
@RequestMapping("/api/admin/token-usage")
public class TokenUsageAdminController {

    private final CostAnalyticsService analyticsService;
    private final CostGovernanceService governanceService;

    public TokenUsageAdminController(CostAnalyticsService analyticsService,
                                     CostGovernanceService governanceService) {
        this.analyticsService = analyticsService;
        this.governanceService = governanceService;
    }

    @GetMapping("/page")
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
        return ApiResponse.ok(analyticsService.usagePage(q, startTime, endTime));
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics(@RequestParam(required = false) String startTime,
                                                       @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(governanceService.tokenStatistics(startTime, endTime));
    }

    /** 单次调用完整成本明细（含账务流水、价格版本、预扣状态）。 */
    @GetMapping("/{requestId}")
    public ApiResponse<Views.UsageDetail> detail(@PathVariable String requestId) {
        return ApiResponse.ok(analyticsService.usageDetail(requestId));
    }
}
