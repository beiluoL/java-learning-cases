package com.example.ai.token.cost.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成本优化执行记录：收益必须标注实测/反推/估算口径。 */
@Data
public class CostOptimizationLog {
    private Long id;
    private String requestId;
    private String strategy;
    private Long beforeTokens;
    private Long afterTokens;
    private Long savedTokens;
    private BigDecimal savedCost;
    private String measureKind;
    private String qualityNote;
    private String detail;
    private LocalDateTime createdAt;
}
