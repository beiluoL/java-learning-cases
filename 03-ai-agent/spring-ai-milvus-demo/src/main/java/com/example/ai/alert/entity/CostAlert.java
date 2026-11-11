package com.example.ai.alert.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算预警与成本异常事件，message 中禁止写入 Prompt 或密钥。 */
@Data
public class CostAlert {
    /** 主键 */
    private Long id;
    /** 关联请求 ID */
    private String requestId;
    /** BUDGET_EXCEEDED / COST_SPIKE / SETTLE_OVERRUN / UNKNOWN_USAGE / REDIS_FAILURE / RECONCILE_DIFF */
    private String alertType;
    /** 等级：INFO / WARNING / HIGH / CRITICAL / BLOCKED */
    private String alertLevel;
    /** 告警对象类型：GLOBAL / USER / MODEL / SCENE / AGENT / KNOWLEDGE_BASE */
    private String targetType;
    /** 关联预算类型 */
    private String budgetType;
    /** 关联预算对象 */
    private String targetId;
    /** 告警说明（不含敏感 Prompt 与密钥） */
    private String message;
    /** 关联金额 */
    private BigDecimal amount;
    /** 触发阈值（预算金额或比率） */
    private BigDecimal thresholdValue;
    /** 触发时实际值 */
    private BigDecimal currentValue;
    /** INFO / WARN / ERROR */
    private String severity;
    /** 状态：OPEN / ACKNOWLEDGED / RESOLVED */
    private String status;
    /** 相同指纹累计发生次数（去重合并） */
    private Integer occurrenceCount;
    /** 去重指纹（类型+对象+等级） */
    private String fingerprint;
    /** 最近一次发生时间(UTC) */
    private LocalDateTime lastSeenAt;
    /** 确认时间(UTC) */
    private LocalDateTime acknowledgedAt;
    /** 确认人 */
    private String acknowledgedBy;
    /** 恢复时间(UTC) */
    private LocalDateTime resolvedAt;
    /** 产生时间(UTC) */
    private LocalDateTime createdAt;
}
