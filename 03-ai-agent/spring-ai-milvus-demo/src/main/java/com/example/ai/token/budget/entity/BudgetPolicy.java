package com.example.ai.token.budget.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算策略：GLOBAL / USER / MODEL / DAILY / MONTHLY / SCENE / KNOWLEDGE_BASE / AGENT / REQUEST。 */
@Data
public class BudgetPolicy {
    private Long id;
    private String budgetType;
    private String targetId;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal perRequestLimit;
    private Long maxInputTokens;
    private Long maxOutputTokens;
    private Integer allowOverrun;
    private Integer enabled;
    private String currency;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
