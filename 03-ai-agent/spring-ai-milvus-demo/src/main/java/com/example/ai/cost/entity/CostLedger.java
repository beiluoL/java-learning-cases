package com.example.ai.cost.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 追加式成本账务流水。历史行不允许 UPDATE/DELETE，
 * 冲正通过新的 ADJUST 反向流水实现，保证账单可审计。
 */
@Data
public class CostLedger {
    /** 主键 */
    private Long id;
    /** 账务流水号，幂等键 */
    private String entryNo;
    /** 类型：MODEL_CALL / EMBEDDING / RERANK / AGENT / INFRA_ALLOCATED / CACHE_SAVING / ADJUST */
    private String entryType;
    /** 口径：ACTUAL 真实账单 / ALLOCATED 分摊 / SAVED 节省（不计入成本合计） */
    private String costKind;
    /** 关联请求 ID */
    private String requestId;
    /** 链路追踪 ID */
    private String traceId;
    /** 用户 ID */
    private String userId;
    /** Agent 标识 */
    private String agentId;
    /** 知识库标识 */
    private String knowledgeBaseId;
    /** 业务场景 */
    private String businessScene;
    /** 供应商 */
    private String provider;
    /** 模型名 */
    private String modelName;
    /** 计费量（Token 数或调用次数） */
    private Long quantity;
    /** 金额（正数为成本/节省，冲正为负数） */
    private BigDecimal amount;
    /** 货币 */
    private String currency;
    /** 价格版本快照 */
    private String pricingVersion;
    /** 备注（不含敏感内容） */
    private String remark;
    /** 业务发生时间(UTC) */
    private LocalDateTime occurredAt;
    /** 入账时间(UTC) */
    private LocalDateTime createdAt;
}
