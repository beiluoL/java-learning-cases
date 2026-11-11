package com.example.ai.token.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** AI 调用明细与成本账单实体，与 ai_token_usage 表一一对应。 */
@Data
public class TokenUsage {
    /** 主键 */
    private Long id;
    /** 请求唯一 ID，幂等键 */
    private String requestId;
    /** 链路追踪 ID */
    private String traceId;
    /** 用户 ID（服务端身份，不信任前端传值） */
    private String userId;
    /** 会话 ID */
    private String conversationId;
    /** Agent 标识（AGENT 场景的任务归属） */
    private String agentId;
    /** 知识库标识（RAG 成本归因） */
    private String knowledgeBaseId;
    /** 业务场景：CHAT / RAG / EMBEDDING / RERANK / QUERY_REWRITE / DOCUMENT_SUMMARY / AGENT */
    private String businessScene;
    /** 模型供应商 */
    private String provider;
    /** 供应商实际返回的模型名 */
    private String modelName;
    /** 模型类型：CHAT / EMBEDDING / RERANK */
    private String modelType;
    /** 输入 Token（含缓存命中部分） */
    private Long inputTokens;
    /** 输出 Token */
    private Long outputTokens;
    /** 总 Token */
    private Long totalTokens;
    /** 缓存命中的输入 Token（input_tokens 的子集，不重复计费） */
    private Long cachedInputTokens;
    /** 推理 Token（按供应商规则，默认不单独计费） */
    private Long reasoningTokens;
    /** Usage 来源：REAL / ESTIMATED / UNAVAILABLE / LOCAL / MOCK */
    private String usageStatus;
    /** 请求状态：SUCCESS / FAILED / BUDGET_REJECTED / UNKNOWN */
    private String requestStatus;
    /** 响应缓存命中：1 命中（未真实调用模型，成本按 0 记账） */
    private Integer cacheHit;
    /** 模型调用耗时(毫秒) */
    private Long latencyMs;
    /** 普通输入成本 */
    private BigDecimal inputCost;
    /** 缓存输入成本 */
    private BigDecimal cachedInputCost;
    /** 输出成本 */
    private BigDecimal outputCost;
    /** 总成本 */
    private BigDecimal totalCost;
    /** 缓存命中节省成本（基于该请求上次真实账单，非新增账单） */
    private BigDecimal cacheSavedCost;
    /** 结算货币 */
    private String currency;
    /** 计费时命中的价格版本快照 */
    private String pricingVersion;
    /** 计费时命中的价格记录 ID 快照 */
    private Long pricingId;
    /** 失败错误码 */
    private String errorCode;
    /** 模型调用重试次数 */
    private Integer retryCount;
    /** 模型路由等级：L1 低成本 / L2 常规 / L3 高能力 */
    private String routeLevel;
    /** 调用时间(UTC) */
    private LocalDateTime createdAt;
}
