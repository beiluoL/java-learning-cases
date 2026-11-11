package com.example.ai.chat.service;

import com.example.ai.agent.AgentCostTracker;
import com.example.ai.budget.TokenBudgetService;
import com.example.ai.budget.entity.BudgetPolicy;
import com.example.ai.budget.mapper.BudgetPolicyMapper;
import com.example.ai.chat.dto.ChatDtos;
import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.ErrorCode;
import com.example.ai.common.MoneyScale;
import com.example.ai.common.TraceContext;
import com.example.ai.common.Views;
import com.example.ai.config.AiProperties;
import com.example.ai.cost.dto.CostBreakdown;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.metrics.AiMetrics;
import com.example.ai.optimization.CostOptimizationService;
import com.example.ai.optimization.ModelRouter;
import com.example.ai.optimization.PromptOptimizer;
import com.example.ai.optimization.RagContextOptimizer;
import com.example.ai.optimization.ResponseCacheService;
import com.example.ai.pricing.entity.ModelPricing;
import com.example.ai.pricing.service.ModelPricingService;
import com.example.ai.protection.AiRequestProtectionService;
import com.example.ai.protection.CostProtectionEngine;
import com.example.ai.token.TokenUsageCollector;
import com.example.ai.token.dto.UsageRecord;
import com.example.ai.token.dto.UsageSnapshot;
import com.example.milvusdemo.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 问答业务编排，串联完整成本治理链路。
 *
 * <p>链路顺序（**顺序不可调整**，否则预扣与结算会错位）：
 * 身份 → 保护等级 → 模型路由 → 限流/并发 → 响应缓存 → 单次上限校验 → 预算预估
 * → Redis 原子预扣（多作用域）→ Agent 闸门 → RAG 检索与上下文压缩 → Prompt 优化
 * → 真实 ChatModel 调用（有限重试）→ 真实 Usage 采集 → 成本计算与账务入账
 * → Redis 结算 → 缓存回填 → 优化收益记录 → 响应组装。</p>
 *
 * <p>Controller 只做 HTTP 转发；所有业务判定、失败补偿与成本记账都在本类，
 * 保证「任何一条调用路径都不会绕过预算与成本核算」。</p>
 *
 * @author Project Team
 * @since 2.0.0
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** 价格表以「每百万 Token 单价」计价，换算时需要除以该常数。 */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final UserIdentityProvider identityProvider;
    private final TokenBudgetService budgetService;
    private final ModelPricingService pricingService;
    private final BudgetPolicyMapper policyMapper;
    private final TokenUsageCollector collector;
    private final ObjectProvider<DocumentIngestionService> ragProvider;
    private final AiMetrics metrics;
    private final Clock clock;
    private final CostProtectionEngine protectionEngine;
    private final AiRequestProtectionService requestGuard;
    private final ModelRouter modelRouter;
    private final ResponseCacheService responseCache;
    private final PromptOptimizer promptOptimizer;
    private final RagContextOptimizer ragContextOptimizer;
    private final CostOptimizationService optimizationService;
    private final AgentCostTracker agentCostTracker;

    public AiChatService(ChatModel chatModel, AiProperties properties, UserIdentityProvider identityProvider,
                         TokenBudgetService budgetService, ModelPricingService pricingService,
                         BudgetPolicyMapper policyMapper, TokenUsageCollector collector,
                         ObjectProvider<DocumentIngestionService> ragProvider, AiMetrics metrics, Clock clock,
                         CostProtectionEngine protectionEngine, AiRequestProtectionService requestGuard,
                         ModelRouter modelRouter, ResponseCacheService responseCache,
                         PromptOptimizer promptOptimizer, RagContextOptimizer ragContextOptimizer,
                         CostOptimizationService optimizationService, AgentCostTracker agentCostTracker) {
        this.chatModel = chatModel;
        this.properties = properties;
        this.identityProvider = identityProvider;
        this.budgetService = budgetService;
        this.pricingService = pricingService;
        this.policyMapper = policyMapper;
        this.collector = collector;
        this.ragProvider = ragProvider;
        this.metrics = metrics;
        this.clock = clock;
        this.protectionEngine = protectionEngine;
        this.requestGuard = requestGuard;
        this.modelRouter = modelRouter;
        this.responseCache = responseCache;
        this.promptOptimizer = promptOptimizer;
        this.ragContextOptimizer = ragContextOptimizer;
        this.optimizationService = optimizationService;
        this.agentCostTracker = agentCostTracker;
    }

    /**
     * 问答主入口：只做链路编排，每一步的业务判定都下沉到具名私有方法。
     *
     * <p>本方法本身不含任何成本计算、门禁判定或异常分类逻辑，
     * 便于按阶段单独阅读与测试；修改链路顺序前请先读 {@link #chat} 的类注释警告。</p>
     *
     * @param request 前端请求体
     * @return 含 Usage / 成本 / 预算 / 优化收益的响应
     */
    public ChatDtos.ChatResponseVO chat(ChatDtos.ChatRequest request) {
        RequestScope scope = RequestScope.of(request, identityProvider.currentUserId(), properties.getProvider());
        TraceContext.put(scope.traceId());
        PermitHolder permit = new PermitHolder();
        try {
            metrics.request();

            RoutedStage routed = openProtectionGate(scope);
            permit.set(requestGuard.acquire(scope.userId(), routed.model()));

            Optional<ResponseCacheService.CachedAnswer> cached = lookupCache(scope, routed);
            if (cached.isPresent()) {
                metrics.cacheHit();
                return serveFromCache(scope, routed, cached.get());
            }

            TokenBudgetService.ReservationResult reservation = reserveBudget(scope, routed);
            guardAgentTask(scope, routed);

            PromptPlan plan = planPrompt(scope, routed);
            long start = System.currentTimeMillis();
            CallOutcome outcome = callWithRetry(plan.prompt());
            long latency = System.currentTimeMillis() - start;

            return completeCall(scope, routed, reservation, plan, outcome, latency);
        } finally {
            requestGuard.release(permit.get(), scope.userId());
            TraceContext.clear();
        }
    }

    // ==================== ① 保护等级 + ② 模型路由 ====================

    /**
     * 保护等级判定与模型路由。
     *
     * <p>被阻断时**先记账再抛出**：账单必须记下这次被拒，
     * 否则「拒绝率」与「被拦下的潜在成本」都无法统计。</p>
     */
    private RoutedStage openProtectionGate(RequestScope scope) {
        AiEnums.ProtectionLevel level = protectionEngine.currentLevel();
        CostProtectionEngine.Decision decision = protectionEngine.evaluate(scope.scene());
        if (!decision.allowed()) {
            metrics.protectionBlocked();
            // 此处尚未路由，记录的模型仍是配置的默认模型，routeLevel 为空——与实际发生的情况一致
            recordFailure(scope, properties.getModel(), null,
                    AiEnums.RequestStatus.PROTECTION_BLOCKED, 0L, "PROTECTION_LEVEL_" + level.code());
            throw new BusinessException(ErrorCode.PROTECTION_BLOCKED, decision.reason());
        }
        ModelRouter.Decision route = modelRouter.route(scope.message(), scope.scene(), level,
                decision.forceLowCostModel());
        if (route.downgraded()) {
            metrics.routeDowngrade();
        }
        return new RoutedStage(level, decision, route.model(), route.level().name());
    }

    // ==================== ④ 响应缓存 ====================

    /** 缓存查询：命中即完全不调用模型，是唯一「零成本」路径。 */
    private Optional<ResponseCacheService.CachedAnswer> lookupCache(RequestScope scope, RoutedStage routed) {
        return responseCache.lookup(cacheKeyOf(scope, routed));
    }

    /** 缓存键：用户 + 知识库 + 实际模型 + 问题与场景，任一变化都必须视为不同请求。 */
    private String cacheKeyOf(RequestScope scope, RoutedStage routed) {
        return responseCache.key(scope.userId(), scope.kbId(), routed.model(),
                scope.message() + "|" + (scope.useRag() ? "rag" : "chat"));
    }

    // ==================== ⑤ 单次上限 + ⑥ 预算预扣 ====================

    /** 单次上限校验后再做多作用域原子预扣；被拒同样先记账再抛出。 */
    private TokenBudgetService.ReservationResult reserveBudget(RequestScope scope, RoutedStage routed) {
        BudgetPolicy userDaily = policyMapper.findScope("DAILY", scope.userId());
        BigDecimal estimated = estimateMaxCost(routed.model(), userDaily);
        enforcePerRequestLimit(userDaily, estimated);
        TokenBudgetService.ReservationResult reservation = budgetService.reserve(
                scope.requestId(), scope.userId(), routed.model(), scope.scene(), scope.kbId(), estimated);
        if (!reservation.approved()) {
            recordFailure(scope, routed.model(), routed.routeLevel(),
                    AiEnums.RequestStatus.BUDGET_REJECTED, 0L, "BUDGET_REJECTED");
            throw BusinessException.budgetRejected(reservation.reason());
        }
        return reservation;
    }

    // ==================== ⑦ Agent 闸门 ====================

    /**
     * Agent 任务闸门：步数 / 工具 / Token / 成本 / 时长 / 循环任一条命中即停止。
     *
     * <p>阻断时必须**先释放预扣**，否则任务被停止后额度仍被占用，后续请求会误判预算不足。</p>
     */
    private void guardAgentTask(RequestScope scope, RoutedStage routed) {
        if (scope.agentTaskId() == null) {
            return;
        }
        agentCostTracker.start(scope.agentTaskId(), scope.agentId(), scope.userId(), scope.conversationId());
        AgentCostTracker.Guard guard = agentCostTracker.guard(scope.agentTaskId(), null);
        if (!guard.allowed()) {
            budgetService.release(scope.requestId());
            recordFailure(scope, routed.model(), routed.routeLevel(),
                    AiEnums.RequestStatus.PROTECTION_BLOCKED, 0L, "AGENT_" + guard.reason());
            throw new BusinessException(ErrorCode.PROTECTION_BLOCKED, "Agent 任务已停止：" + guard.reason());
        }
    }

    // ==================== ⑧ RAG + ⑨ Prompt 优化 ====================

    /** RAG 检索（可选降级）→ 上下文压缩 → Prompt 压缩 → 输出长度约束，最终产出待发送 Prompt。 */
    private PromptPlan planPrompt(RequestScope scope, RoutedStage routed) {
        String context = scope.useRag() && routed.decision().allowRagRewrite()
                ? retrieveContext(scope.message()) : "";
        RagContextOptimizer.Result ragResult = null;
        if (scope.useRag() && !context.isBlank() && properties.getOptimization().isRagContextLimitEnabled()) {
            ragResult = ragContextOptimizer.optimize(context,
                    properties.getOptimization().getMaxContextTokens());
            context = ragResult.context();
        }
        int maxOut = isComplexTask(scope.message())
                ? properties.getOptimization().getMaxOutputTokens()
                : properties.getOptimization().getMaxOutputTokensSimple();
        String rawSystem = "你是企业级 AI 助手。请优先依据提供的参考资料作答；资料不足时明确说明，不要编造。"
                + "请使用简洁中文回答，控制在 " + maxOut + " 字以内。";
        PromptOptimizer.Result promptResult = properties.getOptimization().isPromptCompressionEnabled()
                ? promptOptimizer.optimize(rawSystem) : null;
        String systemText = promptResult == null ? rawSystem : promptResult.after();
        return new PromptPlan(buildPrompt(systemText, context, scope.message()), promptResult, ragResult);
    }

    // ==================== ⑩~⑮ 结果处理 ====================

    /**
     * 模型调用结果处理：失败走补偿，成功走「记账 → 结算 → 缓存回填 → 收益记录 → 组装响应」。
     *
     * <p>失败分支的顺序也是固定的：先补偿预扣（释放或按 UNKNOWN 保守结算），再记账，最后抛出。
     * 顺序反过来会留下「已记账但预扣未释放」的悬挂额度。</p>
     */
    private ChatDtos.ChatResponseVO completeCall(RequestScope scope, RoutedStage routed,
                                                 TokenBudgetService.ReservationResult reservation,
                                                 PromptPlan plan, CallOutcome outcome, long latency) {
        metrics.latency(latency);
        if (outcome.error() != null) {
            throw compensateFailure(scope, routed, reservation, outcome, latency);
        }

        ChatResponse response = outcome.response();
        UsageSnapshot snapshot = collector.extract(scope.provider(), response);
        TokenUsageCollector.CollectResult saved = collector.collect(
                usageRecordOf(scope, routed, snapshot, latency, outcome.retries()));

        CostBreakdown cost = saved.breakdown();
        metrics.cost(cost.totalCost());

        budgetService.settle(scope.requestId(), cost.totalCost(), false);

        String answer = answerTextOf(response);
        // 只缓存成功且非空的答案；失败与空答案不进缓存，避免把错误固化成「命中」
        responseCache.store(cacheKeyOf(scope, routed), cachedAnswerOf(scope, routed, snapshot, cost, answer));
        recordOptimizations(scope.requestId(), routed.model(), plan.promptResult(), plan.ragResult(), snapshot, cost);
        recordAgentStep(scope, snapshot, cost);

        return responseOf(scope, routed, reservation, snapshot, cost, answer, plan.promptResult(), latency);
    }

    /**
     * 失败补偿：区分「确定未送达」与「可能已收费」两种情形。
     *
     * <p>超时 / 读失败等不确定情形按预扣金额保守结算并标记 {@code UNKNOWN}，
     * 由人工对账兜底；确定未送达（连接被拒）才释放预扣。**不可统一释放**，
     * 否则供应商已计费而本地账务为零，形成少算。</p>
     *
     * @return 待抛出的业务异常（由调用方 {@code throw}，以便编译器识别控制流）
     */
    private BusinessException compensateFailure(RequestScope scope, RoutedStage routed,
                                               TokenBudgetService.ReservationResult reservation,
                                               CallOutcome outcome, long latency) {
        if (outcome.uncertain()) {
            budgetService.settle(scope.requestId(), reservation.reserved(), true);
        } else {
            budgetService.release(scope.requestId());
        }
        recordFailure(scope, routed.model(), routed.routeLevel(),
                outcome.uncertain() ? AiEnums.RequestStatus.UNKNOWN : AiEnums.RequestStatus.FAILED,
                latency, outcome.error().getClass().getSimpleName());
        return new BusinessException(ErrorCode.SYSTEM,
                "模型调用失败（" + outcome.error().getClass().getSimpleName() + "），预扣已"
                        + (outcome.uncertain() ? "按 UNKNOWN 保守结算，请人工对账" : "释放"));
    }

    /** 缓存命中：零模型调用、零成本，但必须如实记账并标注 {@code CACHED}。 */
    private ChatDtos.ChatResponseVO serveFromCache(RequestScope scope, RoutedStage routed,
                                                   ResponseCacheService.CachedAnswer cached) {
        BigDecimal savedCost = responseCache.savedCostOf(cached);
        collector.collect(new UsageRecord(scope.requestId(), scope.traceId(), scope.userId(),
                scope.conversationId(), scope.scene(), scope.provider(), cached.model(),
                AiEnums.ModelType.CHAT.name(), cached.inputTokens(), cached.outputTokens(),
                cached.inputTokens() + cached.outputTokens(), cached.cachedInputTokens(),
                cached.reasoningTokens(), AiEnums.UsageStatus.CACHED.name(),
                AiEnums.RequestStatus.SUCCESS.name(), 0L, null, scope.agentId(), scope.kbId(),
                true, savedCost, 0, routed.routeLevel()));

        if (scope.agentTaskId() != null) {
            agentCostTracker.recordStep(scope.agentTaskId(), scope.requestId(),
                    cached.inputTokens(), cached.outputTokens(), BigDecimal.ZERO, null);
        }
        return new ChatDtos.ChatResponseVO(
                scope.requestId(), scope.traceId(), cached.answer(), cached.model(), scope.provider(),
                routed.routeLevel(), true, savedCost.toPlainString(), routed.level().code(),
                new ChatDtos.UsageVO(cached.inputTokens(), cached.outputTokens(),
                        cached.inputTokens() + cached.outputTokens(), cached.cachedInputTokens(),
                        cached.reasoningTokens(), AiEnums.UsageStatus.CACHED.name()),
                new ChatDtos.CostVO("0", "0", "0", "USD", "CACHE_HIT"),
                new ChatDtos.BudgetVO("0", "0", remainingOf(scope.userId())),
                new ChatDtos.OptimizationVO(0, 0, 0, savedCost.toPlainString(),
                        AiEnums.MeasureKind.MEASURED.name()),
                0L);
    }

    /** 组装成功响应；金额一律 {@code toPlainString()}，避免前端拿到科学计数法。 */
    private ChatDtos.ChatResponseVO responseOf(RequestScope scope, RoutedStage routed,
                                               TokenBudgetService.ReservationResult reservation,
                                               UsageSnapshot snapshot, CostBreakdown cost, String answer,
                                               PromptOptimizer.Result promptResult, long latency) {
        return new ChatDtos.ChatResponseVO(
                scope.requestId(), scope.traceId(), answer, snapshot.actualModel(), scope.provider(),
                routed.routeLevel(), false, "0", routed.level().code(),
                new ChatDtos.UsageVO(snapshot.inputTokens(), snapshot.outputTokens(), snapshot.totalTokens(),
                        snapshot.cachedInputTokens(), snapshot.reasoningTokens(), snapshot.usageStatus()),
                new ChatDtos.CostVO(cost.inputCost().toPlainString(), cost.outputCost().toPlainString(),
                        cost.totalCost().toPlainString(), cost.currency(), cost.pricingVersion()),
                new ChatDtos.BudgetVO(reservation.reserved().toPlainString(),
                        cost.totalCost().toPlainString(), remainingOf(scope.userId())),
                optimizationVoOf(promptResult), latency);
    }

    /** Prompt 压缩收益视图：口径固定为 {@code ESTIMATED}（规则估算，未做质量评测）。 */
    private ChatDtos.OptimizationVO optimizationVoOf(PromptOptimizer.Result promptResult) {
        if (promptResult == null) {
            return new ChatDtos.OptimizationVO(0, 0, 0, "0", AiEnums.MeasureKind.ESTIMATED.name());
        }
        return new ChatDtos.OptimizationVO(promptResult.beforeTokens(), promptResult.afterTokens(),
                promptResult.savedTokens(), estimateSavedCost(promptResult.savedTokens()).toPlainString(),
                AiEnums.MeasureKind.ESTIMATED.name());
    }

    /** 取首个预算作用域的剩余额度；无作用域时返回空串而非 "0"，避免前端误显示「已用尽」。 */
    private String remainingOf(String userId) {
        List<Views.BudgetStatus> statuses = budgetService.status(userId);
        return statuses.isEmpty() ? "" : statuses.get(0).remainingAmount();
    }

    private String answerTextOf(ChatResponse response) {
        return response.getResult() == null || response.getResult().getOutput() == null
                ? "" : response.getResult().getOutput().getText();
    }

    private UsageRecord usageRecordOf(RequestScope scope, RoutedStage routed, UsageSnapshot snapshot,
                                      long latency, int retries) {
        return new UsageRecord(scope.requestId(), scope.traceId(), scope.userId(), scope.conversationId(),
                scope.scene(), scope.provider(), snapshot.actualModel(), AiEnums.ModelType.CHAT.name(),
                snapshot.inputTokens(), snapshot.outputTokens(), snapshot.totalTokens(),
                snapshot.cachedInputTokens(), snapshot.reasoningTokens(),
                snapshot.usageStatus(), AiEnums.RequestStatus.SUCCESS.name(), latency, null,
                scope.agentId(), scope.kbId(), false, BigDecimal.ZERO, retries, routed.routeLevel());
    }

    private ResponseCacheService.CachedAnswer cachedAnswerOf(RequestScope scope, RoutedStage routed,
                                                             UsageSnapshot snapshot, CostBreakdown cost,
                                                             String answer) {
        return new ResponseCacheService.CachedAnswer(
                answer, snapshot.actualModel(), scope.provider(), routed.routeLevel(),
                snapshot.inputTokens(), snapshot.outputTokens(), snapshot.cachedInputTokens(),
                snapshot.reasoningTokens(), cost.totalCost().toPlainString(), cost.pricingVersion(),
                clock.millis());
    }

    private void recordAgentStep(RequestScope scope, UsageSnapshot snapshot, CostBreakdown cost) {
        if (scope.agentTaskId() != null) {
            agentCostTracker.recordStep(scope.agentTaskId(), scope.requestId(),
                    snapshot.inputTokens(), snapshot.outputTokens(), cost.totalCost(), null);
        }
    }

    /** 优化收益记录：三类口径分开写，禁止混算。旁路失败不影响主链路。 */
    private void recordOptimizations(String requestId, String routedModel, PromptOptimizer.Result promptResult,
                                     RagContextOptimizer.Result ragResult, UsageSnapshot snapshot,
                                     CostBreakdown cost) {
        try {
            if (promptResult != null && promptResult.savedTokens() > 0) {
                optimizationService.log("PROMPT_COMPRESSION", requestId,
                        promptResult.beforeTokens(), promptResult.afterTokens(),
                        estimateSavedCost(promptResult.savedTokens()), AiEnums.MeasureKind.ESTIMATED,
                        "只做空白归一与重复行删除，语义未改；未做质量评测",
                        "rules=" + String.join(",", promptResult.appliedRules()));
            }
            if (ragResult != null && ragResult.savedTokens() > 0) {
                optimizationService.log("RAG_CONTEXT_LIMIT", requestId,
                        ragResult.beforeTokens(), ragResult.afterTokens(),
                        estimateSavedCost(ragResult.savedTokens()), AiEnums.MeasureKind.ESTIMATED,
                        "上下文去重与截断；Rerank/相似度阈值未启用", ragResult.note());
            }
            // 路由收益：真实 Token × 真实价格的差价（反推口径）
            BigDecimal baseline = baselineCost(routedModel, snapshot);
            BigDecimal savedByRouting = baseline.subtract(cost.totalCost());
            if (savedByRouting.signum() > 0) {
                optimizationService.log("MODEL_ROUTING", requestId,
                        snapshot.totalTokens(), snapshot.totalTokens(), savedByRouting,
                        AiEnums.MeasureKind.COUNTERFACTUAL,
                        "反推口径：同一份真实用量若走基准模型的价格差；未做质量评测",
                        "routedModel=" + routedModel + ",baselineModel=" + properties.getModel());
            }
        } catch (RuntimeException e) {
            log.warn("记录优化收益失败（不影响主链路）: {}", e.getClass().getSimpleName());
        }
    }

    /** 基准模型（配置的默认模型）在相同真实用量下的成本，用于反推路由节省。 */
    private BigDecimal baselineCost(String routedModel, UsageSnapshot snapshot) {
        if (routedModel.equals(properties.getModel())) {
            return BigDecimal.ZERO;
        }
        ModelPricing p = pricingService.findEffective(properties.getProvider(), properties.getModel(),
                AiEnums.ModelType.CHAT.name(), LocalDateTime.now(clock));
        if (p == null) {
            return BigDecimal.ZERO;
        }
        long cached = Math.min(Math.max(snapshot.cachedInputTokens(), 0), Math.max(snapshot.inputTokens(), 0));
        long normal = Math.max(snapshot.inputTokens(), 0) - cached;
        BigDecimal in = p.getInputPrice().multiply(BigDecimal.valueOf(normal)).divide(MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal cachedCost = p.getCachedInputPrice().multiply(BigDecimal.valueOf(cached))
                .divide(MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal out = p.getOutputPrice().multiply(BigDecimal.valueOf(Math.max(snapshot.outputTokens(), 0)))
                .divide(MILLION, 12, RoundingMode.HALF_UP);
        return MoneyScale.normalize(in.add(cachedCost).add(out));
    }

    /** 按价格表与策略的最大 Token 估算「本次可能产生的最大费用」，用于预扣。 */
    private BigDecimal estimateMaxCost(String model, BudgetPolicy policy) {
        BigDecimal fallback = properties.getBudget().getDefaultPerRequestLimit();
        ModelPricing pricing = pricingService.findEffective(properties.getProvider(), model,
                AiEnums.ModelType.CHAT.name(), LocalDateTime.now(clock));
        if (pricing == null) {
            return fallback;
        }
        long maxIn = policy == null || policy.getMaxInputTokens() == null ? 4000L : policy.getMaxInputTokens();
        long maxOut = policy == null || policy.getMaxOutputTokens() == null ? 1000L : policy.getMaxOutputTokens();
        BigDecimal in = pricing.getInputPrice().multiply(BigDecimal.valueOf(maxIn))
                .divide(MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal out = pricing.getOutputPrice().multiply(BigDecimal.valueOf(maxOut))
                .divide(MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal estimate = in.add(out).min(fallback);
        return estimate.signum() <= 0 ? fallback : MoneyScale.normalize(estimate);
    }

    /** 单次请求成本上限：预估已超限时直接拒绝，避免「跑完才发现太贵」。 */
    private void enforcePerRequestLimit(BudgetPolicy policy, BigDecimal estimated) {
        if (policy == null || policy.getPerRequestLimit() == null) {
            return;
        }
        if (estimated.compareTo(policy.getPerRequestLimit()) > 0) {
            throw BusinessException.budgetRejected("预估成本 " + estimated.toPlainString()
                    + " USD 超过单次请求上限 " + policy.getPerRequestLimit().toPlainString() + " USD");
        }
    }

    /** RAG 为可选增强：知识库不可用时不阻断问答，但会明确记日志。 */
    private String retrieveContext(String message) {
        DocumentIngestionService rag = ragProvider.getIfAvailable();
        if (rag == null) {
            log.warn("RAG 服务未装配（可能未启用 Milvus），本次按无知识库模式回答");
            return "";
        }
        try {
            return rag.buildContext(message, properties.getOptimization().getRagTopK());
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为无知识库回答: {}", e.getClass().getSimpleName());
            return "";
        }
    }

    private Prompt buildPrompt(String systemText, String context, String message) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemText));
        if (context != null && !context.isBlank()) {
            messages.add(new SystemMessage("参考资料：\n" + context));
        }
        messages.add(new UserMessage(message));
        return new Prompt(messages);
    }

    /**
     * 写入失败账单。
     *
     * <p>预算被拒、保护阻断、Agent 停止、模型调用失败四条路径都要记账，
     * 因此「账单条数」不等于「成功调用数」，统计时必须按 {@code request_status} 过滤。</p>
     */
    private void recordFailure(RequestScope scope, String model, String routeLevel,
                               AiEnums.RequestStatus status, long latency, String errorCode) {
        try {
            collector.collect(new UsageRecord(scope.requestId(), scope.traceId(), scope.userId(),
                    scope.conversationId(), scope.scene(), scope.provider(), model,
                    AiEnums.ModelType.CHAT.name(),
                    0, 0, 0, 0, 0, AiEnums.UsageStatus.UNAVAILABLE.name(), status.name(), latency, errorCode,
                    scope.agentId(), scope.kbId(), false, BigDecimal.ZERO, 0, routeLevel));
        } catch (RuntimeException e) {
            log.error("写入失败账单出错 requestId={}: {}", scope.requestId(), e.getClass().getSimpleName());
        }
    }

    private BigDecimal estimateSavedCost(int savedTokens) {
        if (savedTokens <= 0) {
            return BigDecimal.ZERO;
        }
        ModelPricing p = pricingService.findEffective(properties.getProvider(), properties.getModel(),
                AiEnums.ModelType.CHAT.name(), LocalDateTime.now(clock));
        if (p == null) {
            return BigDecimal.ZERO;
        }
        return p.getInputPrice().multiply(BigDecimal.valueOf(savedTokens))
                .divide(MILLION, 8, RoundingMode.HALF_UP);
    }

    private boolean isComplexTask(String message) {
        return message != null && message.length() >= properties.getRouting().getComplexCharThreshold();
    }

    // ==================== 模型调用与重试 ====================

    /** 单次模型调用的结果：响应 / 重试次数 / 异常 / 是否「已发出但结果不确定」。 */
    private record CallOutcome(ChatResponse response, int retries, Exception error, boolean uncertain) {
    }

    /**
     * 有限重试：只对「连接被拒绝」这类明确未送达的错误重试。
     *
     * <p>超时与读失败一律不重试——请求可能已被供应商计入用量，
     * 重试会造成重复计费，因此直接按 {@code uncertain} 走保守结算。</p>
     */
    private CallOutcome callWithRetry(Prompt prompt) {
        int maxAttempts = Math.max(properties.getRetry().getMaxAttempts(), 1);
        int retries = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return new CallOutcome(chatModel.call(prompt), retries, null, false);
            } catch (Exception e) {
                boolean uncertain = isUncertainFailure(e);
                boolean canRetry = attempt < maxAttempts && !uncertain && isRetryable(e);
                if (!canRetry) {
                    return new CallOutcome(null, retries, e, uncertain);
                }
                retries++;
                log.warn("模型调用第 {} 次失败（{}），{}ms 后重试", attempt,
                        e.getClass().getSimpleName(), properties.getRetry().getBackoffMs() * attempt);
                sleepQuietly(properties.getRetry().getBackoffMs() * attempt);
            }
        }
        return new CallOutcome(null, retries, new IllegalStateException("重试次数耗尽"), false);
    }

    /** 超时/连接中断类失败无法确认供应商是否已收费，必须走 UNKNOWN 而不是简单释放。 */
    private boolean isUncertainFailure(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof java.util.concurrent.TimeoutException
                    || cur instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (cur instanceof org.springframework.web.client.ResourceAccessException
                    && cur.getCause() instanceof java.net.ConnectException) {
                // 连接被拒绝说明请求未送达，属于可重试的确定性失败
                return false;
            }
            if (cur instanceof org.springframework.web.client.ResourceAccessException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /** 只重试「连接被拒绝」这类明确未送达的错误；超时/读失败一律不重试。 */
    private boolean isRetryable(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            if (cur instanceof java.net.ConnectException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部数据类型 ====================

    /**
     * 单次请求的作用域快照。
     *
     * <p>把原本平铺在方法作用域的 10 个局部变量收敛为一个记录，
     * 使 {@code recordFailure} 之类的方法从 13 个参数降到 6 个。</p>
     *
     * @param requestId      请求唯一 ID（同时是预算幂等键）
     * @param traceId        链路追踪 ID
     * @param userId         服务端身份，绝不信任前端传值
     * @param conversationId 会话 ID，可为空
     * @param agentId        Agent 标识，为空表示非 Agent 调用
     * @param agentTaskId    Agent 任务 ID，非空时启用 Agent 闸门
     * @param kbId           知识库 ID，为空表示不使用特定知识库
     * @param scene          业务场景：CHAT / RAG
     * @param provider       模型供应商标识
     * @param message        用户原始问题
     * @param useRag         是否启用 RAG
     */
    private record RequestScope(String requestId, String traceId, String userId, String conversationId,
                                String agentId, String agentTaskId, String kbId, String scene,
                                String provider, String message, boolean useRag) {

        static RequestScope of(ChatDtos.ChatRequest request, String userId, String provider) {
            boolean useRag = Boolean.TRUE.equals(request.useRag());
            return new RequestScope(
                    TraceContext.newId("req"),
                    TraceContext.newId("trc"),
                    userId,
                    request.conversationId(),
                    trimToNull(request.agentId()),
                    trimToNull(request.agentTaskId()),
                    trimToNull(request.knowledgeBaseId()),
                    useRag ? AiEnums.BusinessScene.RAG.name() : AiEnums.BusinessScene.CHAT.name(),
                    provider,
                    request.message(),
                    useRag);
        }

        private static String trimToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /**
     * 保护与路由结果。
     *
     * @param level      当前保护等级
     * @param decision   保护引擎判定（含是否允许改写、是否强制低成本模型）
     * @param model      实际调用的模型
     * @param routeLevel 路由档位名（L1/L2/L3），未路由时为空
     */
    private record RoutedStage(AiEnums.ProtectionLevel level, CostProtectionEngine.Decision decision,
                               String model, String routeLevel) {
    }

    /**
     * 待发送 Prompt 及其优化结果。
     *
     * @param prompt       最终 Prompt
     * @param promptResult Prompt 压缩结果，未启用或未命中时为 {@code null}
     * @param ragResult    RAG 上下文压缩结果，未启用或未命中时为 {@code null}
     */
    private record PromptPlan(Prompt prompt, PromptOptimizer.Result promptResult,
                              RagContextOptimizer.Result ragResult) {
    }

    /** 许可凭证持有器：permit 在 try 内获取、在 finally 释放，需要跨作用域传递。 */
    private static final class PermitHolder {

        private AiRequestProtectionService.Permit permit;

        AiRequestProtectionService.Permit get() {
            return permit;
        }

        void set(AiRequestProtectionService.Permit permit) {
            this.permit = permit;
        }
    }
}
