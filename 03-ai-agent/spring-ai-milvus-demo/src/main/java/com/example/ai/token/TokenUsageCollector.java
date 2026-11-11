package com.example.ai.token;

import com.example.ai.common.AiEnums;
import com.example.ai.common.MoneyScale;
import com.example.ai.alert.CostAlertService;
import com.example.ai.cost.CostCalculator;
import com.example.ai.cost.CostLedgerService;
import com.example.ai.cost.dto.CostBreakdown;
import com.example.ai.provider.adapter.ProviderUsageAdapter;
import com.example.ai.token.dto.UsageRecord;
import com.example.ai.token.dto.UsageSnapshot;
import com.example.ai.token.entity.TokenUsage;
import com.example.ai.token.mapper.TokenUsageMapper;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一 Token 采集组件：Controller 不自己统计 Token。
 * 职责 = 供应商适配 → 归一结构 → 成本计算 → 幂等持久化 → 账务入账 → 异常告警。
 */
@Service
public class TokenUsageCollector {

    private final List<ProviderUsageAdapter> adapters;
    private final CostCalculator costCalculator;
    private final TokenUsageMapper usageMapper;
    private final CostAlertService alertService;
    private final CostLedgerService ledgerService;
    private final Clock clock;

    public TokenUsageCollector(List<ProviderUsageAdapter> adapters, CostCalculator costCalculator,
                               TokenUsageMapper usageMapper, CostAlertService alertService,
                               CostLedgerService ledgerService, Clock clock) {
        this.adapters = adapters;
        this.costCalculator = costCalculator;
        this.usageMapper = usageMapper;
        this.alertService = alertService;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    /** 从模型响应中提取真实 Usage；没有适配器或供应商不返回 Usage 时明确标 UNAVAILABLE，绝不估算。 */
    public UsageSnapshot extract(String provider, ChatResponse response) {
        for (ProviderUsageAdapter adapter : adapters) {
            if (adapter.supports(provider)) {
                return adapter.extract(response);
            }
        }
        String model = response == null || response.getMetadata() == null
                ? "unknown" : String.valueOf(response.getMetadata().getModel());
        return new UsageSnapshot(0, 0, 0, 0, 0,
                AiEnums.UsageStatus.UNAVAILABLE.name(), model, provider);
    }

    /** 采集并落库；request_id 唯一键 + ON DUPLICATE KEY 保证同一请求不重复计费。 */
    public CollectResult collect(UsageRecord record) {
        LocalDateTime now = LocalDateTime.now(clock);
        // 缓存命中不产生新成本：token 字段保留上次真实用量用于展示，但账单金额为 0
        CostBreakdown breakdown = record.cacheHit()
                ? zeroBreakdown(record)
                : costCalculator.calculate(record, now);

        TokenUsage entity = new TokenUsage();
        entity.setRequestId(record.requestId());
        entity.setTraceId(record.traceId());
        entity.setUserId(record.userId());
        entity.setConversationId(record.conversationId());
        entity.setAgentId(record.agentId());
        entity.setKnowledgeBaseId(record.knowledgeBaseId());
        entity.setBusinessScene(record.businessScene());
        entity.setProvider(record.provider());
        entity.setModelName(record.modelName());
        entity.setModelType(record.modelType());
        entity.setInputTokens(record.inputTokens());
        entity.setOutputTokens(record.outputTokens());
        entity.setTotalTokens(record.totalTokens());
        entity.setCachedInputTokens(record.cachedInputTokens());
        entity.setReasoningTokens(record.reasoningTokens());
        entity.setUsageStatus(record.usageStatus());
        entity.setRequestStatus(record.requestStatus());
        entity.setCacheHit(record.cacheHit() ? 1 : 0);
        entity.setLatencyMs(record.latencyMs());
        entity.setInputCost(breakdown.inputCost());
        entity.setCachedInputCost(breakdown.cachedCost());
        entity.setOutputCost(breakdown.outputCost());
        entity.setTotalCost(breakdown.totalCost());
        entity.setCacheSavedCost(record.cacheSavedCost() == null ? BigDecimal.ZERO : record.cacheSavedCost());
        entity.setCurrency(breakdown.currency());
        entity.setPricingVersion(breakdown.pricingVersion());
        entity.setPricingId(breakdown.pricingId());
        entity.setErrorCode(record.errorCode());
        entity.setRetryCount(record.retryCount());
        entity.setRouteLevel(record.routeLevel());
        entity.setCreatedAt(now);
        usageMapper.insert(entity);

        // 账务流水：真实调用记 ACTUAL；缓存命中记 SAVED（不计入成本合计）
        if (record.cacheHit()) {
            ledgerService.recordCacheSaving(record.requestId(), record.traceId(), record.userId(),
                    record.businessScene(), record.provider(), record.modelName(),
                    record.totalTokens(), record.cacheSavedCost());
        } else if (AiEnums.RequestStatus.SUCCESS.name().equals(record.requestStatus())) {
            ledgerService.recordModelCall(entity);
        }

        if ("UNPRICED".equals(breakdown.pricingVersion())) {
            alertService.alert(AiEnums.AlertType.UNKNOWN_USAGE, null, null,
                    "未找到生效价格，账单按 0 成本记录且标记 UNPRICED: "
                            + record.provider() + "/" + record.modelName(),
                    MoneyScale.normalize(null), "WARN", record.requestId());
        }
        return new CollectResult(entity, breakdown);
    }

    private CostBreakdown zeroBreakdown(UsageRecord r) {
        return new CostBreakdown("CACHE_HIT", null, null, null, null,
                r.inputTokens(), r.cachedInputTokens(), r.outputTokens(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "USD");
    }

    public record CollectResult(TokenUsage usage, CostBreakdown breakdown) {
    }
}
