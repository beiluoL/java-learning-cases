package com.example.ai.budget.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算策略：GLOBAL / USER / MODEL / DAILY / MONTHLY / SCENE / KNOWLEDGE_BASE / AGENT / REQUEST。 */
@Data
public class BudgetPolicy {
    /** 主键 */
    private Long id;
    /** 预算类型：GLOBAL / USER / MODEL / DAILY / MONTHLY */
    private String budgetType;
    /** 预算对象：用户 ID / 模型名 / __ALL__ */
    private String targetId;
    /** 每日最大费用 */
    private BigDecimal dailyLimit;
    /** 每月最大费用 */
    private BigDecimal monthlyLimit;
    /** 单次请求最大费用 */
    private BigDecimal perRequestLimit;
    /** 单次请求最大输入 Token */
    private Long maxInputTokens;
    /** 单次请求最大输出 Token */
    private Long maxOutputTokens;
    /** 是否允许超额：0 严格拦截 1 允许并告警 */
    private Integer allowOverrun;
    /** 是否启用预算控制 */
    private Integer enabled;
    /** 结算货币 */
    private String currency;
    /** 备注 */
    private String remark;
    /** 周期内最大请求数，配合限流使用。 */
    private Integer maxRequests;
    /** 最大并发请求数。 */
    private Integer maxConcurrency;
    /** Agent 任务最大执行步数。 */
    private Integer maxAgentSteps;
    /** Agent 任务最大工具调用次数。 */
    private Integer maxToolCalls;
    /** 预算使用率告警阈值（0~1），为空时用全局默认阈值。 */
    private BigDecimal alertThreshold;
    /** 超额处理策略：BLOCK / DOWNGRADE / ALLOW。 */
    private String overrunPolicy;
    /** 创建时间(UTC) */
    private LocalDateTime createdAt;
    /** 更新时间(UTC) */
    private LocalDateTime updatedAt;
}
