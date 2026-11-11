package com.example.ai.budget.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算变更流水：预扣/结算/释放/过期/人工调整的审计依据。 */
@Data
public class BudgetTransaction {
    /** 主键 */
    private Long id;
    /** 流水号，幂等键 */
    private String txNo;
    /** 关联请求 ID */
    private String requestId;
    /** 预算类型 */
    private String budgetType;
    /** 预算对象 */
    private String targetId;
    /** 用户 */
    private String userId;
    /** RESERVE / SETTLE / RELEASE / EXPIRE / ADJUST */
    private String txType;
    /** 本次变更金额（正=占用，负=归还） */
    private BigDecimal amount;
    /** 变更前已用金额（含预扣） */
    private BigDecimal balanceBefore;
    /** 变更后已用金额（含预扣） */
    private BigDecimal balanceAfter;
    /** 货币 */
    private String currency;
    /** 备注 */
    private String remark;
    /** 发生时间(UTC) */
    private LocalDateTime createdAt;
}
