package com.example.ai.protection.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 保护动作与紧急开关操作日志，必须记录操作人与原因。 */
@Data
public class CostProtectionLog {
    /** 主键 */
    private Long id;
    /** 动作：EMERGENCY_STOP / RESUME / LEVEL_CHANGE / DOWNGRADE / DISABLE_MODEL */
    private String action;
    /** 变更前保护等级 */
    private Integer fromLevel;
    /** 变更后保护等级 */
    private Integer toLevel;
    /** 作用对象类型：GLOBAL / PROVIDER / MODEL / AGENT / USER */
    private String targetType;
    /** 作用对象 ID */
    private String targetId;
    /** 操作人（服务端身份或 system） */
    private String operator;
    /** 操作原因（必填，可审计） */
    private String reason;
    /** 补充说明 */
    private String detail;
    /** 操作时间(UTC) */
    private LocalDateTime createdAt;
    /** 恢复时间(UTC)，NULL 表示仍在生效 */
    private LocalDateTime restoredAt;
}
