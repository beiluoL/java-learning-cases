package com.example.ai.budget.controller;

import com.example.ai.budget.TokenBudgetService;
import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.entity.BudgetTransaction;
import com.example.ai.budget.service.BudgetPolicyService;
import com.example.ai.common.ApiResponse;
import com.example.ai.common.PageResult;
import com.example.ai.common.Views;
import com.example.ai.identity.UserIdentityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预算中心接口（V2 契约路径）：策略分页、编辑、实时状态与变更流水。
 *
 * <p>与 {@link BudgetController}（V1 契约路径）共享同一份字段契约与默认值，
 * 二者都只做 HTTP 适配，业务逻辑在 {@link BudgetPolicyService}。</p>
 */
@RestController
@RequestMapping("/api/admin/budget")
public class BudgetAdminController {

    private final BudgetPolicyService policyService;
    private final TokenBudgetService budgetService;
    private final UserIdentityProvider identityProvider;

    public BudgetAdminController(BudgetPolicyService policyService, TokenBudgetService budgetService,
                                 UserIdentityProvider identityProvider) {
        this.policyService = policyService;
        this.budgetService = budgetService;
        this.identityProvider = identityProvider;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<BudgetPolicy>> page(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String budgetType) {
        return ApiResponse.ok(policyService.page(page, size, budgetType));
    }

    @GetMapping("/status")
    public ApiResponse<List<Views.BudgetStatus>> status() {
        return ApiResponse.ok(budgetService.status(identityProvider.currentUserId()));
    }

    @GetMapping("/{id:[0-9]+}")
    public ApiResponse<BudgetPolicy> get(@PathVariable Long id) {
        return ApiResponse.ok(policyService.getRequired(id));
    }

    @PostMapping
    public ApiResponse<BudgetPolicy> create(@RequestBody BudgetPolicy policy) {
        return ApiResponse.ok(policyService.create(policy));
    }

    @PutMapping("/{id:[0-9]+}")
    public ApiResponse<BudgetPolicy> update(@PathVariable Long id, @RequestBody BudgetPolicy policy) {
        return ApiResponse.ok(policyService.update(id, policy));
    }

    /** 预算变更流水（预扣 / 结算 / 释放 / 过期），用于审计与对账。 */
    @GetMapping("/transactions")
    public ApiResponse<PageResult<BudgetTransaction>> transactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String budgetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Long days) {
        return ApiResponse.ok(policyService.transactions(page, size, budgetType, targetId, days));
    }
}
