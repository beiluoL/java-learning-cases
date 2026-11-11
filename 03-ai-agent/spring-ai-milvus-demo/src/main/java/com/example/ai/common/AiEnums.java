package com.example.ai.common;

/** 平台统一枚举。集中定义避免各处字符串硬编码、大小写不一致。 */
public final class AiEnums {

    private AiEnums() {
    }

    /**
     * Usage 数据来源：真实值必须与估算/模拟严格区分，禁止混入真实账单口径。
     *
     * <ul>
     *   <li>REAL —— 供应商返回的真实 Usage，可用于账单；</li>
     *   <li>ESTIMATED —— 按启发式估算，不能当作真实账单；</li>
     *   <li>UNAVAILABLE —— 供应商未返回 Usage；</li>
     *   <li>LOCAL —— 本地模型计算（无外部账单）；</li>
     *   <li>MOCK —— 模拟模型；</li>
     *   <li>CACHED —— 命中响应缓存，未发生模型调用，成本按 0 记账。</li>
     * </ul>
     */
    public enum UsageStatus {
        REAL, ESTIMATED, UNAVAILABLE, LOCAL, MOCK, CACHED
    }

    public enum RequestStatus {
        SUCCESS, FAILED, BUDGET_REJECTED, UNKNOWN, RATE_LIMITED, PROTECTION_BLOCKED
    }

    public enum BusinessScene {
        CHAT, RAG, QUERY_REWRITE, DOCUMENT_SUMMARY, EMBEDDING, RERANK, AGENT
    }

    public enum ModelType {
        CHAT, EMBEDDING, RERANK
    }

    /** 预算作用域：新增 SCENE / KNOWLEDGE_BASE / AGENT / REQUEST 四级。 */
    public enum BudgetType {
        GLOBAL, USER, MODEL, DAILY, MONTHLY, SCENE, KNOWLEDGE_BASE, AGENT, REQUEST
    }

    public enum ReservationStatus {
        RESERVED, SETTLED, RELEASED, UNKNOWN, EXPIRED
    }

    public enum AlertType {
        BUDGET_EXCEEDED, COST_SPIKE, SETTLE_OVERRUN, UNKNOWN_USAGE, REDIS_FAILURE, RECONCILE_DIFF,
        BUDGET_THRESHOLD, SPEND_VELOCITY, FORECAST_EXHAUST,
        ANOMALY_USER_SPIKE, ANOMALY_TOKEN_SPIKE, ANOMALY_AGENT_LOOP, ANOMALY_RETRY,
        ANOMALY_CACHE_HIT_DROP, ANOMALY_NIGHT_CALL, ANOMALY_ERROR_RATE,
        PROTECTION_ACTION, RATE_LIMITED, CIRCUIT_OPEN
    }

    /** 告警等级，与预算使用率阈值一一对应，可配置。 */
    public enum AlertLevel {
        INFO, WARNING, HIGH, CRITICAL, BLOCKED
    }

    public enum AlertStatus {
        OPEN, ACKNOWLEDGED, RESOLVED
    }

    /** 保护等级：1 正常 / 2 警告 / 3 限制 / 4 严重保护 / 5 预算阻断。 */
    public enum ProtectionLevel {
        NORMAL(1), WARNING(2), LIMIT(3), SEVERE(4), BLOCKED(5);

        private final int code;

        ProtectionLevel(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static ProtectionLevel ofCode(int code) {
            for (ProtectionLevel v : values()) {
                if (v.code == code) {
                    return v;
                }
            }
            return code >= 5 ? BLOCKED : NORMAL;
        }
    }

    /** 成本优化策略：与 ai_cost_optimization_log.strategy 取值一致。 */
    public enum OptimizationStrategy {
        PROMPT_COMPRESSION, CONTEXT_COMPRESSION, RAG_CONTEXT_LIMIT, MODEL_ROUTING,
        RESPONSE_CACHE, EMBEDDING_DEDUP, OUTPUT_TOKEN_LIMIT, PROMPT_CACHING
    }

    /**
     * 优化收益口径，防止把理论值当真实节省：
     * MEASURED 实测、COUNTERFACTUAL 由真实用量与真实价格反推、ESTIMATED 启发式估算。
     */
    public enum MeasureKind {
        MEASURED, COUNTERFACTUAL, ESTIMATED
    }

    /** 账务口径：ACTUAL 真实账单、ALLOCATED 分摊、SAVED 节省（不计入成本合计）。 */
    public enum CostKind {
        ACTUAL, ALLOCATED, SAVED
    }

    public enum LedgerEntryType {
        MODEL_CALL, EMBEDDING, RERANK, AGENT, INFRA_ALLOCATED, CACHE_SAVING, ADJUST
    }

    public enum BudgetTxType {
        RESERVE, SETTLE, RELEASE, EXPIRE, ADJUST
    }

    /** 模型路由等级：L1 低成本、L2 常规、L3 高能力。 */
    public enum RouteLevel {
        L1, L2, L3
    }
}
