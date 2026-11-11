package com.example.ai.optimization.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成本优化执行记录：收益必须标注实测/反推/估算口径。 */
@Data
public class CostOptimizationLog {
    /** 主键 */
    private Long id;
    /** 关联请求 ID */
    private String requestId;
    /**
     * 策略：PROMPT_COMPRESSION / CONTEXT_COMPRESSION / RAG_CONTEXT_LIMIT / MODEL_ROUTING / RESPONSE_CACHE / EMBEDDING_DEDUP / OUTPUT_TOKEN_LIMIT / PROMPT_CACHING
     */
    private String strategy;
    /** 优化前 Token（估算口径见 measure_kind） */
    private Long beforeTokens;
    /** 优化后 Token */
    private Long afterTokens;
    /** 节省 Token */
    private Long savedTokens;
    /** 节省金额 */
    private BigDecimal savedCost;
    /** 口径：MEASURED 实测 / COUNTERFACTUAL 真实用量反推 / ESTIMATED 启发式估算 */
    private String measureKind;
    /** 质量影响说明（未评测必须写明） */
    private String qualityNote;
    /** 执行明细（JSON 文本，不含敏感 Prompt 全文） */
    private String detail;
    /** 记录时间(UTC) */
    private LocalDateTime createdAt;
}
