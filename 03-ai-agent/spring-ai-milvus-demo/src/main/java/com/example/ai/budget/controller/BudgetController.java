package com.example.ai.budget.controller;

import com.example.ai.budget.TokenBudgetService;
import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.service.BudgetPolicyService;
import com.example.ai.common.ApiResponse;
import com.example.ai.common.Views;
import com.example.ai.identity.UserIdentityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 预算策略管理与实时状态（含 Redis 未结算预扣）。
 *
 * <p>本类只负责 HTTP 契约：接收参数、委派给 Service、包装响应。
 * 策略的字段契约与默认值由 {@link BudgetPolicyService} 统一持有。</p>
 */
@RestController
@RequestMapping("/api/admin/ai/budgets")
public class BudgetController {

    private final BudgetPolicyService policyService;
    private final TokenBudgetService budgetService;
    private final UserIdentityProvider identityProvider;

    public BudgetController(BudgetPolicyService policyService, TokenBudgetService budgetService,
                            UserIdentityProvider identityProvider) {
        this.policyService = policyService;
        this.budgetService = budgetService;
        this.identityProvider = identityProvider;
    }

    @GetMapping
    public ApiResponse<List<BudgetPolicy>> list() {
        return ApiResponse.ok(policyService.list());
    }

    /** 当前用户各作用域的实时预算状态（含 Redis 预扣中但未结算的金额）。 */
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
}
