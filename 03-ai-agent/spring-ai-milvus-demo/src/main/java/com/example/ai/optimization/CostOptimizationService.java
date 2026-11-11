package com.example.ai.optimization;

import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import com.example.ai.config.AiProperties;
import com.example.ai.cost.CostLedgerService;
import com.example.ai.optimization.entity.CostOptimizationLog;
import com.example.ai.optimization.mapper.CostOptimizationLogMapper;
import com.example.ai.pricing.entity.ModelPricing;
import com.example.ai.pricing.service.ModelPricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本优化中心：统一记录各策略执行效果，并对外提供「试算」能力。
 *
 * <p>收益口径必须分开看，禁止加总成一个漂亮数字：
 * <ul>
 *   <li>MEASURED —— 真实发生（如响应缓存命中，节省来自上次真实账单）；</li>
 *   <li>COUNTERFACTUAL —— 用真实 Token 用量 × 真实价格表反推（如模型路由）；</li>
 *   <li>ESTIMATED —— 启发式估算（如 Prompt/上下文压缩的 Token 差）。</li>
 * </ul>
 */
@Service
public class CostOptimizationService {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    private final CostOptimizationLogMapper logMapper;
    private final CostLedgerService ledgerService;
    private final PromptOptimizer promptOptimizer;
    private final ContextCompressor contextCompressor;
    private final RagContextOptimizer ragContextOptimizer;
    private final ResponseCacheService responseCache;
    private final EmbeddingCacheService embeddingCache;
    private final ModelRouter modelRouter;
    private final ModelPricingService pricingService;
    private final AiProperties properties;
    private final Clock clock;

    public CostOptimizationService(CostOptimizationLogMapper logMapper, CostLedgerService ledgerService,
                                   PromptOptimizer promptOptimizer, ContextCompressor contextCompressor,
                                   RagContextOptimizer ragContextOptimizer, ResponseCacheService responseCache,
                                   EmbeddingCacheService embeddingCache, ModelRouter modelRouter,
                                   ModelPricingService pricingService, AiProperties properties, Clock clock) {
        this.logMapper = logMapper;
        this.ledgerService = ledgerService;
        this.promptOptimizer = promptOptimizer;
        this.contextCompressor = contextCompressor;
        this.ragContextOptimizer = ragContextOptimizer;
        this.responseCache = responseCache;
        this.embeddingCache = embeddingCache;
        this.modelRouter = modelRouter;
        this.pricingService = pricingService;
        this.properties = properties;
        this.clock = clock;
    }

    public record StrategyInfo(String strategy, String displayName, String trigger, String measureKind,
                               String qualityNote, boolean enabled) {
    }

    public record StrategyStat(String strategy, String displayName, long executionCount, long beforeTokens,
                               long afterTokens, long savedTokens, String savedCost, String measureKind,
                               String qualityNote, String lastExecutedAt) {
    }

    public record CacheStat(long hit, long miss, double hitRate) {
    }

    public record EmbedStat(long generated, long skipped, double skipRate) {
    }

    public record Summary(String measuredSavedCost, String counterfactualSavedCost, String estimatedSavedCost,
                          CacheStat cache, EmbedStat embedding, List<StrategyStat> strategies) {
    }

    public record ExecuteResult(String strategy, int beforeTokens, int afterTokens, int savedTokens,
                                String estimatedSavedCost, String measureKind, String qualityNote, String detail) {
    }

    /** 策略元数据：让前端能解释「这条策略在什么条件下触发、收益怎么算的」。 */
    public List<StrategyInfo> strategies() {
        AiProperties.Optimization o = properties.getOptimization();
        List<StrategyInfo> list = new ArrayList<>();
        list.add(new StrategyInfo("PROMPT_COMPRESSION", "Prompt 优化", "每次组装系统提示词",
                "ESTIMATED", "只做空白归一与重复行删除，语义未改；未做质量评测",
                o.isPromptCompressionEnabled()));
        list.add(new StrategyInfo("CONTEXT_COMPRESSION", "上下文压缩", "多轮对话超过窗口",
                "ESTIMATED", "滑动窗口丢弃较早轮次，无 LLM 摘要；质量影响未评测",
                o.isContextCompressionEnabled()));
        list.add(new StrategyInfo("RAG_CONTEXT_LIMIT", "RAG 上下文限额", "RAG 检索返回后",
                "ESTIMATED", "去重与碎片过滤后按 Token 预算截断；Rerank/相似度阈值未启用",
                o.isRagContextLimitEnabled()));
        list.add(new StrategyInfo("MODEL_ROUTING", "模型路由", "每次请求按复杂度与保护等级",
                "COUNTERFACTUAL", "降级收益 = 真实 Token × (基准模型价 - 实际模型价)；未做质量评测",
                properties.getRouting().isEnabled()));
        list.add(new StrategyInfo("RESPONSE_CACHE", "响应缓存", "相同用户+知识库+模型+Prompt",
                "MEASURED", "命中即跳过模型调用，节省来自上次真实账单；语义缓存未实现",
                properties.getCache().isEnabled()));
        list.add(new StrategyInfo("EMBEDDING_DEDUP", "Embedding 去重", "文档入库前",
                "MEASURED", "内容指纹命中即跳过向量生成；须内容与模型版本都一致",
                o.isEmbeddingDedupEnabled()));
        list.add(new StrategyInfo("OUTPUT_TOKEN_LIMIT", "输出长度控制", "每次请求设置 maxTokens",
                "ESTIMATED", "按场景设定输出上限；未评测是否影响完整性",
                true));
        list.add(new StrategyInfo("PROMPT_CACHING", "Provider Prompt 缓存", "供应商支持时自动生效",
                "MEASURED", "来自供应商返回的 cachedInputTokens，真实计费口径",
                true));
        return list;
    }

    public Summary summary(LocalDateTime from, LocalDateTime to) {
        Map<String, StrategyStat> stats = new LinkedHashMap<>();
        BigDecimal counterfactual = BigDecimal.ZERO;
        BigDecimal estimated = BigDecimal.ZERO;
        for (Map<String, Object> row : logMapper.sumByStrategy(from, to)) {
            String strategy = String.valueOf(row.get("strategy"));
            String kind = row.get("measureKind") == null ? "ESTIMATED" : String.valueOf(row.get("measureKind"));
            BigDecimal saved = decimal(row.get("savedCost"));
            if (AiEnums.MeasureKind.COUNTERFACTUAL.name().equals(kind)) {
                counterfactual = counterfactual.add(saved);
            } else if (AiEnums.MeasureKind.ESTIMATED.name().equals(kind)) {
                estimated = estimated.add(saved);
            }
            stats.put(strategy, new StrategyStat(strategy, displayName(strategy),
                    longOf(row.get("executionCount")), longOf(row.get("beforeTokens")),
                    longOf(row.get("afterTokens")), longOf(row.get("savedTokens")),
                    saved.toPlainString(), kind, qualityNote(strategy),
                    row.get("lastExecutedAt") == null ? "" : String.valueOf(row.get("lastExecutedAt"))));
        }
        // 未执行过的策略也要出现在列表里，避免前端图表缺项
        for (StrategyInfo info : strategies()) {
            stats.putIfAbsent(info.strategy(), new StrategyStat(info.strategy(), info.displayName(),
                    0, 0, 0, 0, "0", info.measureKind(), info.qualityNote(), ""));
        }
        BigDecimal measured = ledgerService.sum(AiEnums.CostKind.SAVED, from, to);
        ResponseCacheService.CacheStats cacheStats = responseCache.stats();
        EmbeddingCacheService.Stats embedStats = embeddingCache.stats();
        return new Summary(measured.toPlainString(), counterfactual.toPlainString(), estimated.toPlainString(),
                new CacheStat(cacheStats.hit(), cacheStats.miss(), cacheStats.hitRate()),
                new EmbedStat(embedStats.generated(), embedStats.skipped(), embedStats.skipRate()),
                new ArrayList<>(stats.values()));
    }

    public PageResult<CostOptimizationLog> history(String strategy, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        long offset = (long) (p - 1) * s;
        return new PageResult<>(logMapper.history(strategy, offset, s),
                logMapper.countHistory(strategy), p, s);
    }

    /** 记录一次真实优化执行；measureKind 必须由调用方如实给出。 */
    public void log(String strategy, String requestId, long beforeTokens, long afterTokens,
                    BigDecimal savedCost, AiEnums.MeasureKind kind, String qualityNote, String detail) {
        CostOptimizationLog l = new CostOptimizationLog();
        l.setRequestId(requestId);
        l.setStrategy(strategy);
        l.setBeforeTokens(beforeTokens);
        l.setAfterTokens(afterTokens);
        l.setSavedTokens(Math.max(beforeTokens - afterTokens, 0));
        l.setSavedCost(savedCost == null ? BigDecimal.ZERO : savedCost);
        l.setMeasureKind(kind.name());
        l.setQualityNote(qualityNote);
        l.setDetail(detail);
        logMapper.insert(l);
    }

    /**
     * 在线试算：只对纯函数策略开放（Prompt / 上下文 / RAG 上下文）。
     * 试算结果标记 ESTIMATED 且 detail 写明 dryRun，绝不混入真实节省。
     */
    public ExecuteResult execute(String strategy, String text) {
        if (text == null || text.isBlank()) {
            throw BusinessException.invalid("text 不能为空");
        }
        if (text.length() > 8000) {
            throw BusinessException.invalid("text 最长 8000 字符");
        }
        AiProperties.Optimization o = properties.getOptimization();
        int before;
        int after;
        String detail;
        switch (strategy) {
            case "PROMPT_COMPRESSION" -> {
                PromptOptimizer.Result r = promptOptimizer.optimize(text);
                before = r.beforeTokens();
                after = r.afterTokens();
                detail = "dryRun=true;rules=" + String.join(",", r.appliedRules());
            }
            case "CONTEXT_COMPRESSION" -> {
                List<ContextCompressor.Turn> turns = new ArrayList<>();
                String[] blocks = text.split("\n{2,}");
                for (int i = 0; i < blocks.length; i++) {
                    if (blocks[i].isBlank()) {
                        continue;
                    }
                    turns.add(new ContextCompressor.Turn(i % 2 == 0 ? "user" : "assistant", blocks[i].trim()));
                }
                ContextCompressor.Result r = contextCompressor.compress(turns, o.getContextWindowRounds(),
                        o.getMaxContextTokens());
                before = r.beforeTokens();
                after = r.afterTokens();
                detail = "dryRun=true;droppedTurns=" + r.droppedTurns() + ";dedupedTurns=" + r.dedupedTurns();
            }
            case "RAG_CONTEXT_LIMIT" -> {
                RagContextOptimizer.Result r = ragContextOptimizer.optimize(text, o.getMaxContextTokens());
                before = r.beforeTokens();
                after = r.afterTokens();
                detail = "dryRun=true;fragments=" + r.fragmentCountBefore() + "->" + r.fragmentCountAfter();
            }
            default -> throw BusinessException.invalid("策略 " + strategy + " 不支持在线试算（属于运行时自动策略）");
        }
        int saved = Math.max(before - after, 0);
        BigDecimal savedCost = estimateCost(saved);
        log(strategy, null, before, after, savedCost, AiEnums.MeasureKind.ESTIMATED,
                "在线试算（dry-run），未接入真实模型调用，未做质量评测", detail);
        return new ExecuteResult(strategy, before, after, saved,
                savedCost.toPlainString(), AiEnums.MeasureKind.ESTIMATED.name(),
                "dry-run 试算，仅用于评估优化空间，不代表已节省真实费用", detail);
    }

    /** 用当前模型输入单价把节省的 Token 折算成金额；无价格则返回 0，绝不编造单价。 */
    private BigDecimal estimateCost(int savedTokens) {
        if (savedTokens <= 0) {
            return BigDecimal.ZERO;
        }
        ModelPricing pricing = pricingService.findEffective(properties.getProvider(), properties.getModel(),
                AiEnums.ModelType.CHAT.name(), LocalDateTime.now(clock));
        if (pricing == null) {
            return BigDecimal.ZERO;
        }
        return pricing.getInputPrice().multiply(BigDecimal.valueOf(savedTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
    }

    private String displayName(String strategy) {
        for (StrategyInfo info : strategies()) {
            if (info.strategy().equals(strategy)) {
                return info.displayName();
            }
        }
        return strategy;
    }

    private String qualityNote(String strategy) {
        for (StrategyInfo info : strategies()) {
            if (info.strategy().equals(strategy)) {
                return info.qualityNote();
            }
        }
        return "未评测";
    }

    private BigDecimal decimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    private long longOf(Object v) {
        if (v == null) {
            return 0L;
        }
        return ((Number) v).longValue();
    }
}
