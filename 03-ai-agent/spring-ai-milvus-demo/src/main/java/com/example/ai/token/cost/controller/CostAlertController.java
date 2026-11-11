package com.example.ai.token.cost.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.PageResult;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.cost.AnomalyDetectionService;
import com.example.ai.token.cost.BudgetThresholdAlertService;
import com.example.ai.token.cost.CostAlertService;
import com.example.ai.token.cost.entity.CostAlert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/** 成本预警中心：分页查询、详情、确认、恢复，以及手动触发两类检测。 */
@RestController
@RequestMapping("/api/admin/cost-alert")
public class CostAlertController {

    private final CostAlertService alertService;
    private final BudgetThresholdAlertService thresholdAlertService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final UserIdentityProvider identityProvider;

    public CostAlertController(CostAlertService alertService,
                               BudgetThresholdAlertService thresholdAlertService,
                               AnomalyDetectionService anomalyDetectionService,
                               UserIdentityProvider identityProvider) {
        this.alertService = alertService;
        this.thresholdAlertService = thresholdAlertService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.identityProvider = identityProvider;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<CostAlert>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long days) {
        LocalDateTime to = LocalDateTime.now().plusMinutes(1);
        LocalDateTime from = to.minus(Math.min(Math.max(days == null ? 7 : days, 1), 92), ChronoUnit.DAYS);
        return ApiResponse.ok(alertService.page(alertType, alertLevel, status, targetType, from, to, page, size));
    }

    @GetMapping("/{id:[0-9]+}")
    public ApiResponse<CostAlert> get(@PathVariable Long id) {
        return ApiResponse.ok(alertService.get(id));
    }

    @PostMapping("/{id:[0-9]+}/acknowledge")
    public ApiResponse<CostAlert> acknowledge(@PathVariable Long id) {
        alertService.acknowledge(id, identityProvider.currentUserId());
        return ApiResponse.ok(alertService.get(id));
    }

    @PostMapping("/{id:[0-9]+}/resolve")
    public ApiResponse<CostAlert> resolve(@PathVariable Long id) {
        alertService.resolve(id);
        return ApiResponse.ok(alertService.get(id));
    }

    /**
     * 手动触发一次预算阈值评估。
     *
     * <p>正常由「结算后事件 + 2 分钟定时扫描」驱动，此接口用于运维验证与调整额度后立即生效，
     * 不改变任何业务数据，只写/恢复告警。
     */
    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("thresholdHits", thresholdAlertService.evaluate());
        return ApiResponse.ok(out);
    }

    /** 手动触发一次异常检测扫描（默认每 10 分钟自动跑一次）。 */
    @PostMapping("/anomaly-scan")
    public ApiResponse<Map<String, Object>> anomalyScan() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("findings", anomalyDetectionService.scan());
        return ApiResponse.ok(out);
    }
}
