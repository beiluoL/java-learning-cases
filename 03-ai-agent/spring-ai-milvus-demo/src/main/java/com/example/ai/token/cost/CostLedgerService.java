package com.example.ai.token.cost;

import com.example.ai.common.AiEnums;
import com.example.ai.common.MoneyScale;
import com.example.ai.token.cost.entity.CostLedger;
import com.example.ai.token.cost.mapper.CostLedgerMapper;
import com.example.ai.token.usage.entity.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 成本账务服务：把「模型调用 / 缓存节省 / 基础设施分摊」统一记为追加式流水。
 *
 * <p>设计要点：
 * <ul>
 *   <li>流水号天然幂等（同一 requestId 只会入账一次），重复消费不会重复计成本；</li>
 *   <li>只 INSERT，不 UPDATE/DELETE；冲正另起 ADJUST 反向流水；</li>
 *   <li>SAVED 口径（缓存节省）不计入成本合计，避免虚增成本；</li>
 *   <li>入账失败不阻断主链路（账单已在 ai_token_usage 落库），但会打 ERROR 日志便于补偿。</li>
 * </ul>
 */
@Service
public class CostLedgerService {

    private static final Logger log = LoggerFactory.getLogger(CostLedgerService.class);

    private final CostLedgerMapper ledgerMapper;
    private final Clock clock;

    public CostLedgerService(CostLedgerMapper ledgerMapper, Clock clock) {
        this.ledgerMapper = ledgerMapper;
        this.clock = clock;
    }

    /** 模型调用入账：ACTUAL 口径，金额与 ai_token_usage 保持一致。 */
    public void recordModelCall(TokenUsage usage) {
        CostLedger l = base("LEDGER-" + usage.getRequestId(), AiEnums.LedgerEntryType.MODEL_CALL,
                AiEnums.CostKind.ACTUAL, usage.getTotalCost() == null ? BigDecimal.ZERO : usage.getTotalCost());
        l.setRequestId(usage.getRequestId());
        l.setTraceId(usage.getTraceId());
        l.setUserId(usage.getUserId());
        l.setAgentId(usage.getAgentId());
        l.setKnowledgeBaseId(usage.getKnowledgeBaseId());
        l.setBusinessScene(usage.getBusinessScene());
        l.setProvider(usage.getProvider());
        l.setModelName(usage.getModelName());
        l.setQuantity(usage.getTotalTokens());
        l.setPricingVersion(usage.getPricingVersion());
        l.setRemark("usage=" + usage.getUsageStatus() + ",status=" + usage.getRequestStatus());
        insertQuietly(l);
    }

    /** 缓存命中节省：SAVED 口径，不计入成本合计，仅用于优化收益分析。 */
    public void recordCacheSaving(String requestId, String traceId, String userId, String scene,
                                  String provider, String modelName, long tokens, BigDecimal savedCost) {
        if (savedCost == null || savedCost.signum() <= 0) {
            return;
        }
        CostLedger l = base("SAVE-" + requestId, AiEnums.LedgerEntryType.CACHE_SAVING,
                AiEnums.CostKind.SAVED, MoneyScale.normalize(savedCost));
        l.setRequestId(requestId);
        l.setTraceId(traceId);
        l.setUserId(userId);
        l.setBusinessScene(scene);
        l.setProvider(provider);
        l.setModelName(modelName);
        l.setQuantity(tokens);
        l.setRemark("响应缓存命中，避免重复调用模型");
        insertQuietly(l);
    }

    /** 基础设施分摊入账：ALLOCATED 口径，绝不与供应商 API 账单混为一谈。 */
    public void recordInfraAllocated(String resourceName, String resourceType, BigDecimal allocatedAmount,
                                     long requestCount, String remark) {
        if (allocatedAmount == null || allocatedAmount.signum() <= 0) {
            return;
        }
        CostLedger l = base("INFRA-" + resourceType + "-" + clock.millis(),
                AiEnums.LedgerEntryType.INFRA_ALLOCATED, AiEnums.CostKind.ALLOCATED,
                MoneyScale.normalize(allocatedAmount));
        l.setProvider("self-hosted");
        l.setModelName(resourceName);
        l.setQuantity(requestCount);
        l.setRemark(remark == null ? "基础设施分摊（ALLOCATED，非供应商账单）" : remark);
        insertQuietly(l);
    }

    public List<CostLedger> listByRequest(String requestId) {
        return ledgerMapper.listByRequestId(requestId);
    }

    public List<CostLedger> recent(int limit) {
        return ledgerMapper.listRecent(Math.min(Math.max(limit, 1), 100));
    }

    public BigDecimal sum(AiEnums.CostKind kind, LocalDateTime from, LocalDateTime to) {
        BigDecimal v = ledgerMapper.sumByKind(from, to, kind.name());
        return v == null ? BigDecimal.ZERO : v;
    }

    public List<Map<String, Object>> breakdown(AiEnums.CostKind kind, LocalDateTime from, LocalDateTime to) {
        return ledgerMapper.breakdown(from, to, kind.name());
    }

    private CostLedger base(String entryNo, AiEnums.LedgerEntryType type, AiEnums.CostKind kind,
                            BigDecimal amount) {
        CostLedger l = new CostLedger();
        l.setEntryNo(entryNo);
        l.setEntryType(type.name());
        l.setCostKind(kind.name());
        l.setAmount(amount == null ? BigDecimal.ZERO : amount);
        l.setCurrency("USD");
        l.setOccurredAt(LocalDateTime.now(clock));
        return l;
    }

    private void insertQuietly(CostLedger l) {
        try {
            ledgerMapper.insert(l);
        } catch (DuplicateKeyException e) {
            // 幂等：同一流水号重复消费，忽略即可
        } catch (RuntimeException e) {
            log.error("成本流水入账失败 entryNo={} type={}: {}", l.getEntryNo(), l.getEntryType(),
                    e.getClass().getSimpleName());
        }
    }
}
