package com.example.ai.budget.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 预扣流水：RESERVED / SETTLED / RELEASED / UNKNOWN / EXPIRED，request_id 唯一。 */
@Data
public class BudgetReservation {
    /** 主键 */
    private Long id;
    /** 请求唯一 ID */
    private String requestId;
    /** 预算类型 */
    private String budgetType;
    /** 预算对象 */
    private String targetId;
    /** 发起用户 */
    private String userId;
    /** 预扣金额 */
    private BigDecimal reservedAmount;
    /** 实际结算金额 */
    private BigDecimal settledAmount;
    /** RESERVED / SETTLED / RELEASED / UNKNOWN / EXPIRED */
    private String status;
    /** 预扣过期时间(UTC) */
    private LocalDateTime expireAt;
    /** 创建时间(UTC) */
    private LocalDateTime createdAt;
    /** 更新时间(UTC) */
    private LocalDateTime updatedAt;
}
