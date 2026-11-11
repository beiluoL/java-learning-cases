package com.example.ai.token.budget.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import com.example.ai.common.Views;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.token.budget.TokenBudgetService;
import com.example.ai.token.budget.entity.BudgetPolicy;
import com.example.ai.token.budget.entity.BudgetTransaction;
import com.example.ai.token.budget.mapper.BudgetPolicyMapper;
import com.example.ai.token.budget.mapper.BudgetTransactionMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 预算中心接口（V2 契约路径）：策略分页、编辑、实时状态与变更流水。 */
@RestController
@RequestMapping("/api/admin/budget")
public class BudgetAdminController {

    private final BudgetPolicyMapper policyMapper;
    private final BudgetTransactionMapper txMapper;
    private final TokenBudgetService budgetService;
    private final UserIdentityProvider identityProvider;

    public BudgetAdminController(BudgetPolicyMapper policyMapper, BudgetTransactionMapper txMapper,
                                 TokenBudgetService budgetService, UserIdentityProvider identityProvider) {
        this.policyMapper = policyMapper;
        this.txMapper = txMapper;
        this.budgetService = budgetService;
        this.identityProvider = identityProvider;
    }

    @GetMapping("/page")
    public ApiResponse<PageResult<BudgetPolicy>> page(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String budgetType) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        List<BudgetPolicy> all = policyMapper.list().stream()
                .filter(x -> budgetType == null || budgetType.isBlank()
                        || budgetType.equalsIgnoreCase(x.getBudgetType()))
                .toList();
        int from = Math.min((p - 1) * s, all.size());
        int to = Math.min(from + s, all.size());
        List<BudgetPolicy> slice = new ArrayList<>(all.subList(from, to));
        return ApiResponse.ok(new PageResult<>(slice, all.size(), p, s));
    }

    @GetMapping("/status")
    public ApiResponse<List<Views.BudgetStatus>> status() {
        return ApiResponse.ok(budgetService.status(identityProvider.currentUserId()));
    }

    @GetMapping("/{id:[0-9]+}")
    public ApiResponse<BudgetPolicy> get(@PathVariable Long id) {
        BudgetPolicy p = policyMapper.findById(id);
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
        policy.setOverrunPolicy(policy.getOverrunPolicy() == null ? "BLOCK" : policy.getOverrunPolicy());
        policyMapper.insert(policy);
        return ApiResponse.ok(policy);
    }

    @PutMapping("/{id:[0-9]+}")
    public ApiResponse<BudgetPolicy> update(@PathVariable Long id, @RequestBody BudgetPolicy policy) {
        get(id);
        policy.setId(id);
        policyMapper.update(policy);
        return ApiResponse.ok(policyMapper.findById(id));
    }

    /** 预算变更流水（预扣/结算/释放/过期），用于审计与对账。 */
    @GetMapping("/transactions")
    public ApiResponse<PageResult<BudgetTransaction>> transactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String budgetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Long days) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long offset = (long) (p - 1) * s;
        LocalDateTime to = LocalDateTime.now().plusMinutes(1);
        LocalDateTime from = to.minus(Math.min(Math.max(days == null ? 7 : days, 1), 92), ChronoUnit.DAYS);
        return ApiResponse.ok(new PageResult<>(
                txMapper.page(budgetType, targetId, from, to, offset, s),
                txMapper.count(budgetType, targetId, from, to), p, s));
    }
}
