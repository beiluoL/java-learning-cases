package com.example.ai.chat.service;

import com.example.ai.chat.dto.ChatDtos;
import com.example.ai.common.AiEnums;
import com.example.ai.common.BusinessException;
import com.example.ai.common.ErrorCode;
import com.example.ai.common.MoneyScale;
import com.example.ai.common.TraceContext;
import com.example.ai.common.Views;
import com.example.ai.config.AiProperties;
import com.example.ai.guard.AIRequestProtectionService;
import com.example.ai.identity.UserIdentityProvider;
import com.example.ai.metrics.AiMetrics;
import com.example.ai.optimize.CostOptimizationService;
import com.example.ai.optimize.ModelRouter;
import com.example.ai.optimize.PromptOptimizer;
import com.example.ai.optimize.RagContextOptimizer;
import com.example.ai.optimize.ResponseCacheService;
import com.example.ai.token.agent.AgentCostTracker;
import com.example.ai.token.budget.TokenBudgetService;
import com.example.ai.token.budget.entity.BudgetPolicy;
import com.example.ai.token.budget.mapper.BudgetPolicyMapper;
import com.example.ai.token.cost.CostProtectionEngine;
import com.example.ai.token.cost.dto.CostBreakdown;
import com.example.ai.token.pricing.entity.ModelPricing;
import com.example.ai.token.pricing.service.ModelPricingService;
import com.example.ai.token.usage.TokenUsageCollector;
import com.example.ai.token.usage.dto.UsageRecord;
import com.example.ai.token.usage.dto.UsageSnapshot;
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
 * AI 问答业务编排，完整治理链路：
 * 身份 → 保护等级 → 模型路由 → 限流/并发 → 响应缓存 → 单次上限校验 → 预算预估
 * → Redis 原子预扣（多作用域）→ RAG 检索与上下文压缩 → Prompt 优化 → 真实 ChatModel 调用（有限重试）
 * → 真实 Usage 采集 → 成本计算与账务入账 → Redis 结算 → 缓存回填 → 优化收益记录 → 返回。
 *
 * <p>Controller 只做 HTTP 转发；所有业务判定、失败补偿与成本记账都在这里，
 * 保证「任何一条调用路径都不会绕过预算与成本核算」。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
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
    private final AIRequestProtectionService requestGuard;
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
                         CostProtectionEngine protectionEngine, AIRequestProtectionService requestGuard,
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

    public ChatDtos.ChatResponseVO chat(ChatDtos.ChatRequest request) {
        // userId 只取服务端身份，绝不信任前端传值
        String userId = identityProvider.currentUserId();
        String requestId = TraceContext.newId("req");
        String traceId = TraceContext.newId("trc");
        TraceContext.put(traceId);
        String provider = properties.getProvider();
        boolean useRag = Boolean.TRUE.equals(request.useRag());
        String scene = useRag ? AiEnums.BusinessScene.RAG.name() : AiEnums.BusinessScene.CHAT.name();
        String kbId = normalize(request.knowledgeBaseId());
        String agentId = normalize(request.agentId());
        String agentTaskId = normalize(request.agentTaskId());
        AIRequestProtectionService.Permit permit = null;
        String routedModel = properties.getModel();
        String routeLevel = null;

        try {
            metrics.request();

            // ① 保护等级：预算使用率/紧急开关/未恢复告警共同决定
            AiEnums.ProtectionLevel level = protectionEngine.currentLevel();
            CostProtectionEngine.Decision decision = protectionEngine.evaluate(scene);
            if (!decision.allowed()) {
                metrics.protectionBlocked();
                recordFailure(requestId, traceId, userId, request.conversationId(), agentId, kbId, scene,
                        provider, routedModel, AiEnums.RequestStatus.PROTECTION_BLOCKED, 0L,
                        "PROTECTION_LEVEL_" + level.code(), routeLevel);
                throw new BusinessException(ErrorCode.PROTECTION_BLOCKED, decision.reason());
            }

            // ② 模型路由：只做「降级安全」方向，且目标模型必须有生效价格
            ModelRouter.Decision route = modelRouter.route(request.message(), scene, level,
                    decision.forceLowCostModel());
            routedModel = route.model();
            routeLevel = route.level().name();
            if (route.downgraded()) {
                metrics.routeDowngrade();
            }

            // ③ 限流与并发
            permit = requestGuard.acquire(userId, routedModel);

            // ④ 响应缓存：命中即完全不调用模型
            String cacheKey = responseCache.key(userId, kbId, routedModel,
                    request.message() + "|" + (useRag ? "rag" : "chat"));
            Optional<ResponseCacheService.CachedAnswer> cached = responseCache.lookup(cacheKey);
            if (cached.isPresent()) {
                metrics.cacheHit();
                return serveFromCache(requestId, traceId, userId, request, agentId, kbId, scene,
                        provider, routedModel, routeLevel, level, cached.get());
            }

            // ⑤ 单次成本上限与预算预估
            BudgetPolicy userDaily = policyMapper.findScope("DAILY", userId);
            BigDecimal estimated = estimateMaxCost(routedModel, userDaily);
            enforcePerRequestLimit(userDaily, estimated);

            // ⑥ Redis 原子预扣（DAILY/MONTHLY/USER/MODEL/SCENE/KB/AGENT 多作用域）
            TokenBudgetService.ReservationResult reservation =
                    budgetService.reserve(requestId, userId, routedModel, scene, kbId, estimated);
            if (!reservation.approved()) {
                recordFailure(requestId, traceId, userId, request.conversationId(), agentId, kbId, scene,
                        provider, routedModel, AiEnums.RequestStatus.BUDGET_REJECTED, 0L,
                        "BUDGET_REJECTED", routeLevel);
                throw BusinessException.budgetRejected(reservation.reason());
            }

            // ⑦ Agent 闸门：步数/工具/Token/成本/时长/循环
            if (agentTaskId != null) {
                agentCostTracker.start(agentTaskId, agentId, userId, request.conversationId());
                AgentCostTracker.Guard guard = agentCostTracker.guard(agentTaskId, null);
                if (!guard.allowed()) {
                    budgetService.release(requestId);
                    recordFailure(requestId, traceId, userId, request.conversationId(), agentId, kbId, scene,
                            provider, routedModel, AiEnums.RequestStatus.PROTECTION_BLOCKED, 0L,
                            "AGENT_" + guard.reason(), routeLevel);
                    throw new BusinessException(ErrorCode.PROTECTION_BLOCKED,
                            "Agent 任务已停止：" + guard.reason());
                }
            }

            // ⑧ RAG 检索 + 上下文压缩
            String context = useRag && decision.allowRagRewrite() ? retrieveContext(request.message()) : "";
            RagContextOptimizer.Result ragResult = null;
            if (useRag && !context.isBlank() && properties.getOptimization().isRagContextLimitEnabled()) {
                ragResult = ragContextOptimizer.optimize(context,
                        properties.getOptimization().getMaxContextTokens());
                context = ragResult.context();
            }

            // ⑨ Prompt 优化 + 输出长度约束（通过指令约束，非伪造 token 参数）
            int maxOut = isComplexTask(request.message())
                    ? properties.getOptimization().getMaxOutputTokens()
                    : properties.getOptimization().getMaxOutputTokensSimple();
            String rawSystem = "你是企业级 AI 助手。请优先依据提供的参考资料作答；资料不足时明确说明，不要编造。"
                    + "请使用简洁中文回答，控制在 " + maxOut + " 字以内。";
            PromptOptimizer.Result promptResult = properties.getOptimization().isPromptCompressionEnabled()
                    ? promptOptimizer.optimize(rawSystem) : null;
            String systemText = promptResult == null ? rawSystem : promptResult.after();
            Prompt prompt = buildPrompt(systemText, context, request.message());

            // ⑩ 模型调用（有限重试：仅对明确未送达的错误重试，超时不重试避免重复计费）
            long start = System.currentTimeMillis();
            CallOutcome outcome = callWithRetry(prompt);
            long latency = System.currentTimeMillis() - start;

            if (outcome.error() != null) {
                metrics.latency(latency);
                if (outcome.uncertain()) {
                    // 已发给供应商但响应不确定：按预扣金额保守计入并标记 UNKNOWN，等待人工对账
                    budgetService.settle(requestId, reservation.reserved(), true);
                } else {
                    budgetService.release(requestId);
                }
                recordFailure(requestId, traceId, userId, request.conversationId(), agentId, kbId, scene,
                        provider, routedModel,
                        outcome.uncertain() ? AiEnums.RequestStatus.UNKNOWN : AiEnums.RequestStatus.FAILED,
                        latency, outcome.error().getClass().getSimpleName(), routeLevel);
                throw new BusinessException(ErrorCode.SYSTEM,
                        "模型调用失败（" + outcome.error().getClass().getSimpleName() + "），预扣已"
                                + (outcome.uncertain() ? "按 UNKNOWN 保守结算，请人工对账" : "释放"));
            }

            ChatResponse response = outcome.response();
            metrics.latency(latency);

            // ⑪ 真实 Usage 采集 + 成本计算 + 账单入账
            UsageSnapshot snapshot = collector.extract(provider, response);
            TokenUsageCollector.CollectResult saved = collector.collect(
                    new UsageRecord(requestId, traceId, userId, request.conversationId(), scene,
                            provider, snapshot.actualModel(), AiEnums.ModelType.CHAT.name(),
                            snapshot.inputTokens(), snapshot.outputTokens(), snapshot.totalTokens(),
                            snapshot.cachedInputTokens(), snapshot.reasoningTokens(),
                            snapshot.usageStatus(), AiEnums.RequestStatus.SUCCESS.name(), latency, null,
                            agentId, kbId, false, BigDecimal.ZERO, outcome.retries(), routeLevel));

            CostBreakdown cost = saved.breakdown();
            metrics.cost(cost.totalCost());

            // ⑫ Redis 结算（幂等）
            budgetService.settle(requestId, cost.totalCost(), false);

            String answer = response.getResult() == null || response.getResult().getOutput() == null
                    ? "" : response.getResult().getOutput().getText();

            // ⑬ 缓存回填：只缓存成功且非空的答案
            responseCache.store(cacheKey, new ResponseCacheService.CachedAnswer(
                    answer, snapshot.actualModel(), provider, routeLevel,
                    snapshot.inputTokens(), snapshot.outputTokens(), snapshot.cachedInputTokens(),
                    snapshot.reasoningTokens(), cost.totalCost().toPlainString(), cost.pricingVersion(),
                    clock.millis()));

            // ⑭ 优化收益记录（口径分开：估算 vs 真实用量反推）
            recordOptimizations(requestId, routedModel, promptResult, ragResult, snapshot, cost);

            // ⑮ Agent 任务累计
            if (agentTaskId != null) {
                agentCostTracker.recordStep(agentTaskId, requestId,
                        snapshot.inputTokens(), snapshot.outputTokens(), cost.totalCost(), null);
            }

            List<Views.BudgetStatus> statuses = budgetService.status(userId);
            String remaining = statuses.isEmpty() ? "" : statuses.get(0).remainingAmount();
            ChatDtos.OptimizationVO optVo = promptResult == null
                    ? new ChatDtos.OptimizationVO(0, 0, 0, "0", AiEnums.MeasureKind.ESTIMATED.name())
                    : new ChatDtos.OptimizationVO(promptResult.beforeTokens(), promptResult.afterTokens(),
                    promptResult.savedTokens(), estimateSavedCost(promptResult.savedTokens()).toPlainString(),
                    AiEnums.MeasureKind.ESTIMATED.name());

            return new ChatDtos.ChatResponseVO(
                    requestId, traceId, answer, snapshot.actualModel(), provider, routeLevel,
                    false, "0", level.code(),
                    new ChatDtos.UsageVO(snapshot.inputTokens(), snapshot.outputTokens(), snapshot.totalTokens(),
                            snapshot.cachedInputTokens(), snapshot.reasoningTokens(), snapshot.usageStatus()),
                    new ChatDtos.CostVO(cost.inputCost().toPlainString(), cost.outputCost().toPlainString(),
                            cost.totalCost().toPlainString(), cost.currency(), cost.pricingVersion()),
                    new ChatDtos.BudgetVO(reservation.reserved().toPlainString(),
                            cost.totalCost().toPlainString(), remaining),
                    optVo, latency);
        } finally {
            requestGuard.release(permit, userId);
            TraceContext.clear();
        }
    }

    /** 缓存命中：零模型调用、零成本，但必须如实记账并标注 CACHED。 */
    private ChatDtos.ChatResponseVO serveFromCache(String requestId, String traceId, String userId,
                                                   ChatDtos.ChatRequest request, String agentId, String kbId,
                                                   String scene, String provider, String model, String routeLevel,
                                                   AiEnums.ProtectionLevel level,
                                                   ResponseCacheService.CachedAnswer cached) {
        BigDecimal savedCost = responseCache.savedCostOf(cached);
        collector.collect(new UsageRecord(requestId, traceId, userId, request.conversationId(), scene,
                provider, cached.model(), AiEnums.ModelType.CHAT.name(),
                cached.inputTokens(), cached.outputTokens(),
                cached.inputTokens() + cached.outputTokens(), cached.cachedInputTokens(),
                cached.reasoningTokens(), AiEnums.UsageStatus.CACHED.name(),
                AiEnums.RequestStatus.SUCCESS.name(), 0L, null,
                agentId, kbId, true, savedCost, 0, routeLevel));

        if (request.agentTaskId() != null) {
            agentCostTracker.recordStep(request.agentTaskId(), requestId,
                    cached.inputTokens(), cached.outputTokens(), BigDecimal.ZERO, null);
        }
        List<Views.BudgetStatus> statuses = budgetService.status(userId);
        String remaining = statuses.isEmpty() ? "" : statuses.get(0).remainingAmount();
        return new ChatDtos.ChatResponseVO(
                requestId, traceId, cached.answer(), cached.model(), provider, routeLevel,
                true, savedCost.toPlainString(), level.code(),
                new ChatDtos.UsageVO(cached.inputTokens(), cached.outputTokens(),
                        cached.inputTokens() + cached.outputTokens(), cached.cachedInputTokens(),
                        cached.reasoningTokens(), AiEnums.UsageStatus.CACHED.name()),
                new ChatDtos.CostVO("0", "0", "0", "USD", "CACHE_HIT"),
                new ChatDtos.BudgetVO("0", "0", remaining),
                new ChatDtos.OptimizationVO(0, 0, 0, savedCost.toPlainString(),
                        AiEnums.MeasureKind.MEASURED.name()),
                0L);
    }

    private record CallOutcome(ChatResponse response, int retries, Exception error, boolean uncertain) {
    }

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

    /** 优化收益记录：三类口径分开写，禁止混算。 */
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

    private void recordFailure(String requestId, String traceId, String userId, String conversationId,
                               String agentId, String kbId, String scene, String provider, String model,
                               AiEnums.RequestStatus status, long latency, String errorCode, String routeLevel) {
        try {
            collector.collect(new UsageRecord(requestId, traceId, userId, conversationId, scene,
                    provider, model, AiEnums.ModelType.CHAT.name(),
                    0, 0, 0, 0, 0, AiEnums.UsageStatus.UNAVAILABLE.name(), status.name(), latency, errorCode,
                    agentId, kbId, false, BigDecimal.ZERO, 0, routeLevel));
        } catch (RuntimeException e) {
            log.error("写入失败账单出错 requestId={}: {}", requestId, e.getClass().getSimpleName());
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

    private String normalize(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
