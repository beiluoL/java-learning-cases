package com.example.ai.protection.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 自动成本保护规则：条件 + 等级 + 动作，管理员可开关与调阈值。 */
@Data
public class CostProtectionRule {
    /** 主键 */
    private Long id;
    /** 规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String ruleName;
    /** 生效保护等级 1~5 */
    private Integer protectionLevel;
    /** 触发条件：BUDGET_USAGE_RATE / DAILY_COST / ERROR_RATE / CACHE_HIT_RATE / MANUAL */
    private String conditionType;
    /** 条件阈值（比率为 0~1） */
    private BigDecimal thresholdValue;
    /** 动作：NOTIFY / DOWNGRADE_MODEL / LIMIT_CONCURRENCY / DISABLE_AGENT / BLOCK_REQUEST / EMERGENCY_STOP */
    private String action;
    /** 优先级，数字越小越优先 */
    private Integer priority;
    /** 是否启用 */
    private Integer enabled;
    /** 说明 */
    private String remark;
    /** 创建时间(UTC) */
    private LocalDateTime createdAt;
    /** 更新时间(UTC) */
    private LocalDateTime updatedAt;
}
