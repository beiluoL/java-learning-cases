package com.example.ai.budget.service;

import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.entity.BudgetTransaction;
import com.example.ai.budget.mapper.BudgetPolicyMapper;
import com.example.ai.budget.mapper.BudgetTransactionMapper;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 预算策略的增删改查与变更流水查询。
 *
 * <p><b>为什么需要这个类（而不是让 Controller 直接调 Mapper）：</b>
 * 两个预算 Controller（{@code /api/admin/ai/budgets}、{@code /api/admin/budget}）
 * 原本各自注入 {@link BudgetPolicyMapper} 并各自实现了一遍「校验 + 补默认值 + 分页 + 不存在判断」。
 * 两份实现的默认值补全不一致，导致其中一个接口必然写库失败（详见 {@link #applyCreateDefaults}）。
 * 收敛到本 Service 后，两条 HTTP 路径共享同一份字段契约。</p>
 *
 * <p><b>字段契约来源：</b>下表为 {@code information_schema.COLUMNS} 对 {@code ai_budget_policy}
 * 的实测结果。凡是 {@code NOT NULL} 且有数据库默认值的列，MyBatis 的 {@code INSERT}
 * <b>显式传参</b>时 {@code null} 不会被数据库默认值兜住，必须在 Java 侧补全。</p>
 *
 * <pre>
 * allow_overrun   NOT NULL DEFAULT 0       ← 应用写入
 * enabled         NOT NULL DEFAULT 1       ← 应用写入
 * currency        NOT NULL DEFAULT 'USD'   ← 应用写入
 * overrun_policy  NOT NULL DEFAULT 'BLOCK' ← 应用写入
 * budget_type     NOT NULL（无默认值）      ← 必填，调用方提供
 * target_id       NOT NULL DEFAULT '__ALL__'
 * </pre>
 */
@Service
public class BudgetPolicyService {

    /** 超额处理策略默认值，与数据库列默认值保持一致。 */
    private static final String DEFAULT_OVERRUN_POLICY = "BLOCK";
    /** 是否允许超额默认值：0 = 严格拦截。 */
    private static final int DEFAULT_ALLOW_OVERRUN = 0;
    /** 结算货币默认值。 */
    private static final String DEFAULT_CURRENCY = "USD";
    /** 是否启用默认值：1 = 启用。 */
    private static final int DEFAULT_ENABLED = 1;

    /** 单页最大条数，防止调用方传入极大值拖垮内存。 */
    private static final int MAX_PAGE_SIZE = 100;
    /** 流水查询默认回看天数。 */
    private static final int DEFAULT_LOOKBACK_DAYS = 7;
    /** 流水查询最大回看天数。 */
    private static final int MAX_LOOKBACK_DAYS = 92;

    private final BudgetPolicyMapper policyMapper;
    private final BudgetTransactionMapper txMapper;
    private final Clock clock;

    public BudgetPolicyService(BudgetPolicyMapper policyMapper, BudgetTransactionMapper txMapper, Clock clock) {
        this.policyMapper = policyMapper;
        this.txMapper = txMapper;
        this.clock = clock;
    }

    /** 全量预算策略（按 budget_type、target_id 排序）。 */
    public List<BudgetPolicy> list() {
        return policyMapper.list();
    }

    /** 按主键查询，不存在时抛 404 语义的业务异常。 */
    public BudgetPolicy getRequired(Long id) {
        BudgetPolicy policy = policyMapper.findById(id);
        if (policy == null) {
            throw BusinessException.notFound("预算策略不存在: " + id);
        }
        return policy;
    }

    /**
     * 新建预算策略。
     *
     * <p>入参中 {@code id} 会被强制清空（防止调用方传入 id 触发非预期更新），
     * 其余 NOT NULL 列的缺省值由 {@link #applyCreateDefaults} 补全。</p>
     */
    public BudgetPolicy create(BudgetPolicy policy) {
        if (policy == null) {
            throw BusinessException.invalid("请求体不能为空");
        }
        if (policy.getBudgetType() == null || policy.getTargetId() == null) {
            throw BusinessException.invalid("budgetType / targetId 不能为空");
        }
        policy.setId(null);
        applyCreateDefaults(policy);
        policyMapper.insert(policy);
        return policyMapper.findById(policy.getId());
    }

    /**
     * 更新预算策略。
     *
     * <p>{@code UPDATE} 语句会整列覆盖 NOT NULL 列，因此请求体中缺失的字段
     * 必须从库中原值回填，否则会以 {@code null} 覆盖并被数据库拒绝。</p>
     */
    public BudgetPolicy update(Long id, BudgetPolicy policy) {
        if (policy == null) {
            throw BusinessException.invalid("请求体不能为空");
        }
        BudgetPolicy existing = getRequired(id);
        policy.setId(id);
        policy.setEnabled(policy.getEnabled() == null ? existing.getEnabled() : policy.getEnabled());
        policy.setOverrunPolicy(policy.getOverrunPolicy() == null
                ? existing.getOverrunPolicy() : policy.getOverrunPolicy());
        policy.setAllowOverrun(policy.getAllowOverrun() == null
                ? existing.getAllowOverrun() : policy.getAllowOverrun());
        policyMapper.update(policy);
        return policyMapper.findById(id);
    }

    /**
     * 预算策略分页。
     *
     * <p>沿用「全量加载后内存过滤」的既有实现，不改为 SQL 分页：
     * 该表是人工维护的配置表（当前 7 行），改为 SQL 分页属于超出本次治理范围的性能改造。</p>
     */
    public PageResult<BudgetPolicy> page(int page, int size, String budgetType) {
        int safePage = Math.max(page, 1);
        int safeSize = clampPageSize(size);
        List<BudgetPolicy> all = policyMapper.list().stream()
                .filter(x -> budgetType == null || budgetType.isBlank()
                        || budgetType.equalsIgnoreCase(x.getBudgetType()))
                .toList();
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<BudgetPolicy> slice = new ArrayList<>(all.subList(from, to));
        return new PageResult<>(slice, all.size(), safePage, safeSize);
    }

    /** 预算变更流水（预扣 / 结算 / 释放 / 过期），用于审计与对账。 */
    public PageResult<BudgetTransaction> transactions(int page, int size, String budgetType,
                                                     String targetId, Long days) {
        int safePage = Math.max(page, 1);
        int safeSize = clampPageSize(size);
        long offset = (long) (safePage - 1) * safeSize;
        long lookback = Math.min(Math.max(days == null ? DEFAULT_LOOKBACK_DAYS : days, 1), MAX_LOOKBACK_DAYS);
        LocalDateTime to = LocalDateTime.now(clock).plusMinutes(1);
        LocalDateTime from = to.minus(lookback, ChronoUnit.DAYS);
        List<BudgetTransaction> records = txMapper.page(budgetType, targetId, from, to, offset, safeSize);
        long total = txMapper.count(budgetType, targetId, from, to);
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /**
     * 补全新建时的 NOT NULL 列默认值。
     *
     * <p><b>缺陷背景（本方法存在的原因）：</b>
     * {@code BudgetPolicyMapper.xml} 的 {@code INSERT} 把所有列都写成显式占位符，
     * 因此 Java 侧传 {@code null} 会真的插入 {@code NULL}，数据库列默认值不生效，
     * MySQL 严格模式直接拒绝：{@code Column 'overrun_policy' cannot be null}。
     *
     * <p>修复前 {@code /api/admin/ai/budgets} 只补了 {@code enabled}，
     * {@code /api/admin/budget} 补了 {@code enabled} 与 {@code overrunPolicy}，
     * 两者都漏了 {@code allowOverrun} 与 {@code currency}，所以<b>两条路径的新建都会 500</b>。</p>
     */
    private void applyCreateDefaults(BudgetPolicy policy) {
        if (policy.getEnabled() == null) {
            policy.setEnabled(DEFAULT_ENABLED);
        }
        if (policy.getAllowOverrun() == null) {
            policy.setAllowOverrun(DEFAULT_ALLOW_OVERRUN);
        }
        if (policy.getCurrency() == null || policy.getCurrency().isBlank()) {
            policy.setCurrency(DEFAULT_CURRENCY);
        }
        if (policy.getOverrunPolicy() == null || policy.getOverrunPolicy().isBlank()) {
            policy.setOverrunPolicy(DEFAULT_OVERRUN_POLICY);
        }
    }

    private static int clampPageSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
