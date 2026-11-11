package com.example.ai.token.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 调用明细分页查询条件。
 *
 * <p>时间范围与分页大小由 Service 再次强制约束，防止全表扫描；
 * 超出 {@code ai.analytics.max-page-size} 的 size 会被压回上限。</p>
 *
 * <p><b>排序安全约束</b>：{@link #sort} 会以 {@code ORDER BY ${sort}} 形式拼入 SQL，
 * 属于字符串插值而非参数绑定，因此**白名单校验必须发生在赋值时**，
 * 由本类的 {@link #setSort(String)} 统一保证。历史上的做法是在 Service 里过滤，
 * 只要有任意一个调用方直接给 {@code sort} 赋值就会形成注入面，故下沉到 DTO。</p>
 */
@Data
public class UsageQuery {

    /** 允许的排序表达式白名单，与 TokenUsageMapper.xml 的列名严格对应。 */
    public static final Set<String> ALLOWED_SORT = Set.of(
            "created_at DESC", "created_at ASC", "total_cost DESC", "total_cost ASC", "latency_ms DESC");

    /** 前端契约里的 camelCase 逗号写法 → SQL 列名表达式。 */
    private static final Map<String, String> SORT_ALIASES = Map.of(
            "createdAt,DESC", "created_at DESC", "createdAt,ASC", "created_at ASC",
            "totalCost,DESC", "total_cost DESC", "totalCost,ASC", "total_cost ASC",
            "latencyMs,DESC", "latency_ms DESC");

    /** 默认排序值。 */
    public static final String DEFAULT_SORT = "created_at DESC";

    /** 页码，从 1 开始。 */
    private int page = 1;

    /** 每页条数，默认 20。 */
    private int size = 20;

    /** 用户 ID 过滤条件。 */
    private String userId;

    /** 模型名过滤条件。 */
    private String modelName;

    /** 业务场景过滤条件：CHAT / RAG / EMBEDDING / AGENT。 */
    private String businessScene;

    /** 请求状态过滤条件：SUCCESS / FAILED / BUDGET_REJECTED / PROTECTION_BLOCKED / UNKNOWN。 */
    private String requestStatus;

    /** Usage 来源过滤条件：REAL / ESTIMATED / UNAVAILABLE / LOCAL / MOCK / CACHED。 */
    private String usageStatus;

    /** 最小成本（含）。 */
    private BigDecimal minCost;

    /** 最大成本（含）。 */
    private BigDecimal maxCost;

    /** 查询区间开始（UTC）。 */
    private LocalDateTime startTime;

    /** 查询区间结束（UTC）。 */
    private LocalDateTime endTime;

    /** 排序表达式，赋值时自动做别名映射与白名单校验，非法值回落为默认排序。 */
    private String sort = DEFAULT_SORT;

    /**
     * 设置排序表达式。
     *
     * <p>先做 camelCase 别名映射，再校验白名单；命中不了白名单（含 {@code null}、
     * 空串、注入串）一律回落为 {@link #DEFAULT_SORT}，保证拼进 SQL 的永远是已知常量。</p>
     *
     * <p>注意：{@code Set.of(...).contains(null)} 会抛 NPE，因此必须显式先判空，
     * 不能依赖白名单集合兜住 {@code null}。</p>
     *
     * @param sort 前端传入的排序写法，可为 {@code null}
     */
    public void setSort(String sort) {
        if (sort == null) {
            this.sort = DEFAULT_SORT;
            return;
        }
        String mapped = SORT_ALIASES.getOrDefault(sort, sort);
        this.sort = ALLOWED_SORT.contains(mapped) ? mapped : DEFAULT_SORT;
    }
}
