package com.example.ai.token.cost.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.cost.CostAlertService;
import com.example.ai.token.cost.CostProtectionEngine;
import com.example.ai.token.cost.entity.CostProtectionLog;
import com.example.ai.token.cost.entity.CostProtectionRule;
import com.example.ai.optimize.ModelRouter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 系统保护策略：规则查看/调整、紧急停用与恢复、操作日志、模型路由档位。
 *
 * <p>紧急停用与恢复必须填写原因；操作人取自服务端身份，不接受前端传入。
 */
@RestController
@RequestMapping("/api/admin/cost-protection")
public class CostProtectionController {

    private final CostProtectionEngine engine;
    private final CostAlertService alertService;
    private final ModelRouter modelRouter;
    private final UserIdentityProvider identityProvider;

    public CostProtectionController(CostProtectionEngine engine, CostAlertService alertService,
                                    ModelRouter modelRouter, UserIdentityProvider identityProvider) {
        this.engine = engine;
        this.alertService = alertService;
        this.modelRouter = modelRouter;
        this.identityProvider = identityProvider;
    }

    public record RuleUpdate(Integer protectionLevel, BigDecimal thresholdValue, String action,
                             Integer priority, Integer enabled, String remark) {
    }

    public record ReasonRequest(String reason) {
    }

    @GetMapping("/rules")
    public ApiResponse<List<CostProtectionRule>> rules() {
        return ApiResponse.ok(engine.rules());
    }

    @PutMapping("/rules/{ruleCode}")
    public ApiResponse<CostProtectionRule> updateRule(@PathVariable String ruleCode,
                                                      @RequestBody RuleUpdate body) {
        return ApiResponse.ok(engine.updateRule(ruleCode, body.protectionLevel(), body.thresholdValue(),
                body.action(), body.priority(), body.enabled(), body.remark()));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "level", engine.currentLevel().name(),
                "levelCode", engine.currentLevel().code(),
                "emergencyStopped", engine.isEmergencyStopped(),
                "maxBudgetUsageRate", engine.maxBudgetUsageRate(),
                "openCriticalAlerts", alertService.countOpenByLevel(com.example.ai.common.AiEnums.AlertLevel.CRITICAL),
                "routes", modelRouter.describe()));
    }

    @PostMapping("/emergency-stop")
    public ApiResponse<Map<String, Object>> emergencyStop(@RequestBody ReasonRequest body) {
        if (body == null || body.reason() == null || body.reason().isBlank()) {
            throw BusinessException.invalid("必须填写停用原因");
        }
        engine.emergencyStop(operator(), body.reason());
        return ApiResponse.ok(Map.of("emergencyStopped", true, "reason", body.reason()));
    }

    @PostMapping("/resume")
    public ApiResponse<Map<String, Object>> resume(@RequestBody(required = false) ReasonRequest body) {
        engine.resume(operator(), body == null ? null : body.reason());
        return ApiResponse.ok(Map.of("emergencyStopped", false));
    }

    @GetMapping("/logs")
    public ApiResponse<List<CostProtectionLog>> logs(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(engine.logs(limit));
    }

    private String operator() {
        return identityProvider.currentUserId() + "(admin)";
    }
}
