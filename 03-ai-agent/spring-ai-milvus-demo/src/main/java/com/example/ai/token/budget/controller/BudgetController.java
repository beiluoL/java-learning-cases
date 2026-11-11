package com.example.ai.token.budget.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.common.Views;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.budget.TokenBudgetService;
import com.example.ai.token.budget.entity.BudgetPolicy;
import com.example.ai.token.budget.mapper.BudgetPolicyMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 预算策略管理与实时状态（含 Redis 未结算预扣）。 */
@RestController
@RequestMapping("/api/admin/ai/budgets")
public class BudgetController {

    private final BudgetPolicyMapper mapper;
    private final TokenBudgetService budgetService;
    private final UserIdentityProvider identityProvider;

    public BudgetController(BudgetPolicyMapper mapper, TokenBudgetService budgetService,
                            UserIdentityProvider identityProvider) {
        this.mapper = mapper;
        this.budgetService = budgetService;
        this.identityProvider = identityProvider;
    }

    @GetMapping
    public ApiResponse<List<BudgetPolicy>> list() {
        return ApiResponse.ok(mapper.list());
    }

    @GetMapping("/status")
    public ApiResponse<List<Views.BudgetStatus>> status() {
        return ApiResponse.ok(budgetService.status(identityProvider.currentUserId()));
    }

    @GetMapping("/{id:[0-9]+}")
    public ApiResponse<BudgetPolicy> get(@PathVariable Long id) {
        BudgetPolicy p = mapper.findById(id);
        if (p == null) {
            throw BusinessException.notFound("预算策略不存在: " + id);
        }
        return ApiResponse.ok(p);
    }

    @PostMapping
    public ApiResponse<BudgetPolicy> create(@RequestBody BudgetPolicy policy) {
        if (policy.getBudgetType() == null || policy.getTargetId() == null) {
            throw BusinessException.invalid("budgetType / targetId 不能为空");
        }
        policy.setId(null);
        policy.setEnabled(policy.getEnabled() == null ? 1 : policy.getEnabled());
        mapper.insert(policy);
        return ApiResponse.ok(policy);
    }

    @PutMapping("/{id:[0-9]+}")
    public ApiResponse<BudgetPolicy> update(@PathVariable Long id, @RequestBody BudgetPolicy policy) {
        get(id);
        policy.setId(id);
        mapper.update(policy);
        return ApiResponse.ok(mapper.findById(id));
    }
}
