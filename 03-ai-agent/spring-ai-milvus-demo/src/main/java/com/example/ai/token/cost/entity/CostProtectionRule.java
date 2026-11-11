package com.example.ai.token.cost.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 自动成本保护规则：条件 + 等级 + 动作，管理员可开关与调阈值。 */
@Data
public class CostProtectionRule {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private Integer protectionLevel;
    private String conditionType;
    private BigDecimal thresholdValue;
    private String action;
    private Integer priority;
    private Integer enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
