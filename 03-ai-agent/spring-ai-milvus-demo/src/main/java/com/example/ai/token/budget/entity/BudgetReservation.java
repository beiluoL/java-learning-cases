package com.example.ai.token.budget.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预扣流水：RESERVED / SETTLED / RELEASED / UNKNOWN / EXPIRED，request_id 唯一。 */
@Data
public class BudgetReservation {
    private Long id;
    private String requestId;
    private String budgetType;
    private String targetId;
    private String userId;
    private BigDecimal reservedAmount;
    private BigDecimal settledAmount;
    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
