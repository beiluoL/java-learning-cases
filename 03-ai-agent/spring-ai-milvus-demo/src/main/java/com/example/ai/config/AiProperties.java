package com.example.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** AI 供应商与治理策略配置，全部可由环境变量覆盖，禁止硬编码密钥。 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** mock = 可控模拟模型；openai = OpenAI 兼容接口（含 DeepSeek）。 */
    private String provider = "mock";
    private String model = "deepseek-chat";
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private Budget budget = new Budget();
    private Analytics analytics = new Analytics();
    private Cache cache = new Cache();
    private Routing routing = new Routing();
    private RateLimit rateLimit = new RateLimit();
    private Protection protection = new Protection();
    private Optimization optimization = new Optimization();
    private Agent agent = new Agent();
    private Retry retry = new Retry();

    @Data
    public static class Budget {
        private boolean enabled = true;
        private int reservationTtlSeconds = 300;
        private BigDecimal defaultDailyLimit = new BigDecimal("5.00");
        private BigDecimal defaultMonthlyLimit = new BigDecimal("100.00");
        private BigDecimal defaultPerRequestLimit = new BigDecimal("0.10");
    }

    @Data
    public static class Analytics {
        private int maxRangeDays = 92;
        private int maxPageSize = 100;
    }

    /** 响应缓存：精确匹配，key 含模型/知识库/用户/提示词版本，避免跨租户串答案。 */
    @Data
    public static class Cache {
        private boolean enabled = true;
        private int ttlSeconds = 600;
        private int maxAnswerChars = 12000;
        /** 提示词模板版本，模板变更必须改这里，否则会命中旧模板答案。 */
        private String promptVersion = "v1";
    }

    /** 模型路由：三档模型名与复杂度阈值，均可外部化配置。 */
    @Data
    public static class Routing {
        private boolean enabled = true;
        private String l1Model = "";
        private String l2Model = "";
        private String l3Model = "";
        /** 问题长度超过该值视为复杂任务（启发式，非质量评测结论）。 */
        private int complexCharThreshold = 240;
        /** 保护等级达到该值时强制路由到最低档模型。 */
        private int downgradeAtLevel = 3;
    }

    /** 限流与并发：Redis 计数器实现，默认值保守，避免误伤正常使用。 */
    @Data
    public static class RateLimit {
        private boolean enabled = true;
        private int perUserPerMinute = 30;
        private int perUserPerHour = 300;
        private int perUserPerDay = 2000;
        private int perModelConcurrency = 8;
        private int globalConcurrency = 32;
    }

    @Data
    public static class Protection {
        private boolean enabled = true;
        /** 计算保护等级时是否纳入告警中心未恢复的 CRITICAL 告警。 */
        private boolean considerOpenAlerts = true;
        /** 紧急开关在 Redis 中的 key，置 1 表示全局停用付费调用。 */
        private String emergencyKey = "ai:{governance}:emergency-stop";
    }

    /** 成本优化开关与阈值。关闭任一开关即回到未优化链路，便于对照实验。 */
    @Data
    public static class Optimization {
        private boolean promptCompressionEnabled = true;
        private boolean contextCompressionEnabled = true;
        private boolean ragContextLimitEnabled = true;
        private int contextWindowRounds = 4;
        private int maxContextTokens = 1500;
        private int ragTopK = 4;
        private double ragSimilarityThreshold = 0.2d;
        private int ragCandidateLimit = 12;
        private int maxOutputTokens = 800;
        private int maxOutputTokensSimple = 400;
        private boolean embeddingDedupEnabled = true;
    }

    /** Agent 防失控上限：步数/工具调用/Token/成本/时长任一触顶即停止。 */
    @Data
    public static class Agent {
        private int maxSteps = 8;
        private int maxToolCalls = 16;
        private long maxTokens = 60000L;
        private BigDecimal maxCost = new BigDecimal("0.20");
        private int maxDurationSeconds = 120;
        private int maxRepeatedToolCalls = 3;
    }

    /**
     * 重试策略。默认<b>不重试超时</b>：超时无法确认供应商是否已计费，
     * 重试可能造成重复扣费，因此只重试「连接被拒绝」这类明确未送达的错误。
     */
    @Data
    public static class Retry {
        private int maxAttempts = 2;
        private long backoffMs = 300;
        private boolean retryOnTimeout = false;
    }
}
