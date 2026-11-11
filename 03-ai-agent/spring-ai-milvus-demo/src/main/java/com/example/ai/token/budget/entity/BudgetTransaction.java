package com.example.ai.token.budget.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预算变更流水：预扣/结算/释放/过期/人工调整的审计依据。 */
@Data
public class BudgetTransaction {
    private Long id;
    private String txNo;
    private String requestId;
    private String budgetType;
    private String targetId;
    private String userId;
    private String txType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String currency;
    private String remark;
    private LocalDateTime createdAt;
}
