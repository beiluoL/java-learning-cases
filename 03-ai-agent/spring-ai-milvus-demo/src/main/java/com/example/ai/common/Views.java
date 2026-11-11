package com.example.ai.common;

import com.example.ai.token.cost.dto.CostBreakdown;
import com.example.ai.token.usage.entity.TokenUsage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 统计与详情 VO。金额对外一律字符串（避免 JS 精度丢失），比率一律 0~1 的小数。 */
public final class Views {

    private Views() {
    }

    public record Overview(String totalCost, long totalTokens, long inputTokens, long outputTokens,
                           long cachedInputTokens, long requestCount, String avgCostPerRequest,
                           double successRate, double budgetUsageRate) {
    }

    public record TrendPoint(String bucket, String totalCost, long inputTokens, long outputTokens,
                             long requestCount) {
    }

    public record ModelCost(String provider, String modelName, long requestCount, long inputTokens,
                            long outputTokens, String totalCost, String avgCost, double costRatio) {
    }

    public record SceneCost(String businessScene, long requestCount, long totalTokens,
                            String totalCost, double costRatio) {
    }

    public record TopItem(String name, String value) {
    }

    public record BudgetStatus(String budgetType, String targetId, String limitAmount, String usedAmount,
                               String reservedAmount, String remainingAmount, double usageRate) {
    }

    /** 调用详情：账单 + 成本计算过程 + 价格版本 + 预扣状态，默认不含完整 Prompt。 */
    public record UsageDetail(TokenUsage usage, CostBreakdown costBreakdown, Map<String, Object> reservation) {
    }

    public record AlertItem(Long id, String alertType, String budgetType, String message,
                            String severity, String createdAt) {
    }

    /** 成本构成：按账务类型聚合，costKind 明确是真实账单还是分摊。 */
    public record BreakdownItem(String entryType, String costKind, long entryCount, String amount,
                                double costRatio) {
    }

    public record UserCost(String userId, long requestCount, String totalCost, double costRatio) {
    }

    /** 成本构成总览：真实 API 成本与基础设施分摊分开给出，禁止相加后冒充真实账单。 */
    public record CostBreakdownView(String apiCost, String allocatedCost, String savedCost,
                                    List<BreakdownItem> items) {
    }

    /** Mapper 返回值的轻量数值转换工具，集中处理 BigDecimal / Number 的空值。 */
    public static BigDecimal decimal(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }

    public static long longOf(Object v) {
        if (v == null) {
            return 0L;
        }
        return ((Number) v).longValue();
    }

    public static String money(Object v) {
        return decimal(v).toPlainString();
    }
}
