package com.example.ai.token.cost.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 保护动作与紧急开关操作日志，必须记录操作人与原因。 */
@Data
public class CostProtectionLog {
    private Long id;
    private String action;
    private Integer fromLevel;
    private Integer toLevel;
    private String targetType;
    private String targetId;
    private String operator;
    private String reason;
    private String detail;
    private LocalDateTime createdAt;
    private LocalDateTime restoredAt;
}
