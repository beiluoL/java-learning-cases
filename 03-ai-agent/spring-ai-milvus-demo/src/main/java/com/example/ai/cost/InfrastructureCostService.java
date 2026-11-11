package com.example.ai.cost;

import com.example.ai.common.BusinessException;
import com.example.ai.common.Views;
import com.example.ai.analytics.mapper.CostAnalyticsMapper;
import com.example.ai.cost.entity.InfrastructureCost;
import com.example.ai.cost.mapper.InfrastructureCostMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础设施成本：登记云资源/自建资源的周期成本，并提供「按请求量分摊」能力。
 *
 * <p>必须分清三种口径：
 * <ul>
 *   <li>METERED —— 供应商能直接计量（如对象存储流量），可信度高；</li>
 *   <li>ALLOCATED —— 固定月费按请求量分摊，只能算估算；</li>
 *   <li>FIXED —— 与 AI 调用无关的固定成本，不分摊。</li>
 * </ul>
 * 分摊结果写入账务流水时固定为 {@code ALLOCATED}，绝不与供应商 API 的真实账单混算。
 * 当前库内 DEMO 数据 demo_flag=1，不代表任何真实云账单。
 */
@Service
public class InfrastructureCostService {

    private final InfrastructureCostMapper mapper;
    private final CostAnalyticsMapper analyticsMapper;
    private final CostLedgerService ledgerService;

    public InfrastructureCostService(InfrastructureCostMapper mapper, CostAnalyticsMapper analyticsMapper,
                                     CostLedgerService ledgerService) {
        this.mapper = mapper;
        this.analyticsMapper = analyticsMapper;
        this.ledgerService = ledgerService;
    }

    public record AllocationResult(int resourceCount, int allocatedCount, long requestCount,
                                   String totalAmount, String unitCostPerRequest, String note) {
    }

    public List<InfrastructureCost> list() {
        return mapper.list();
    }

    public InfrastructureCost create(InfrastructureCost cost) {
        validate(cost);
        cost.setAllocatedAmount(BigDecimal.ZERO);
        cost.setCurrency(cost.getCurrency() == null ? "USD" : cost.getCurrency());
        cost.setAllocationMethod(cost.getAllocationMethod() == null ? "ALLOCATED" : cost.getAllocationMethod());
        cost.setDemoFlag(cost.getDemoFlag() == null ? 1 : cost.getDemoFlag());
        mapper.insert(cost);
        return cost;
    }

    public InfrastructureCost update(Long id, InfrastructureCost patch) {
        InfrastructureCost existing = mapper.findById(id);
        if (existing == null) {
            throw BusinessException.notFound("基础设施成本记录不存在: " + id);
        }
        patch.setId(id);
        validate(patch);
        mapper.update(patch);
        return mapper.findById(id);
    }

    /** 按请求量把周期内固定成本分摊到每次调用；已分摊过的资源不重复分摊。 */
    public AllocationResult allocate(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) {
            throw BusinessException.invalid("periodStart / periodEnd 不合法");
        }
        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodEnd.plusDays(1).atStartOfDay();
        long requestCount = Views.longOf(analyticsMapper.overview(from, to).get("requestCount"));

        List<InfrastructureCost> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int allocated = 0;
        for (InfrastructureCost row : mapper.list()) {
            boolean overlaps = !row.getPeriodStart().isAfter(periodEnd) && !row.getPeriodEnd().isBefore(periodStart);
            if (!overlaps) {
                continue;
            }
            rows.add(row);
            total = total.add(row.getAmount() == null ? BigDecimal.ZERO : row.getAmount());
            boolean already = row.getAllocatedAmount() != null && row.getAllocatedAmount().signum() > 0;
            if (already || "FIXED".equals(row.getAllocationMethod()) || requestCount == 0) {
                continue;
            }
            mapper.updateAllocated(row.getId(), row.getAmount(), requestCount);
            ledgerService.recordInfraAllocated(row.getResourceName(), row.getResourceType(), row.getAmount(),
                    requestCount, row.getRemark());
            allocated++;
        }
        BigDecimal unit = requestCount == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(requestCount), 8, RoundingMode.HALF_UP);
        String note = requestCount == 0
                ? "周期内没有请求，未执行分摊"
                : "已按周期请求量分摊；ALLOCATED 口径为估算，与供应商 API 账单分开统计";
        return new AllocationResult(rows.size(), allocated, requestCount,
                total.toPlainString(), unit.toPlainString(), note);
    }

    /** 成本构成分析用：按资源类型汇总真实登记额与已分摊额。 */
    public List<Map<String, Object>> breakdown() {
        Map<String, BigDecimal[]> byType = new LinkedHashMap<>();
        for (InfrastructureCost c : mapper.list()) {
            BigDecimal[] v = byType.computeIfAbsent(c.getResourceType(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            v[0] = v[0].add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
            v[1] = v[1].add(c.getAllocatedAmount() == null ? BigDecimal.ZERO : c.getAllocatedAmount());
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byType.forEach((type, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resourceType", type);
            m.put("amount", v[0].toPlainString());
            m.put("allocatedAmount", v[1].toPlainString());
            m.put("costKind", "ALLOCATED");
            out.add(m);
        });
        return out;
    }

    public BigDecimal totalAmount(LocalDate start, LocalDate end) {
        BigDecimal v = mapper.sumAmount(start, end);
        return v == null ? BigDecimal.ZERO : v;
    }

    private void validate(InfrastructureCost c) {
        if (c.getResourceType() == null || c.getResourceType().isBlank()) {
            throw BusinessException.invalid("resourceType 不能为空");
        }
        if (c.getAmount() == null || c.getAmount().signum() < 0) {
            throw BusinessException.invalid("amount 不能为空且不能为负数");
        }
        if (c.getPeriodStart() == null || c.getPeriodEnd() == null || c.getPeriodStart().isAfter(c.getPeriodEnd())) {
            throw BusinessException.invalid("计费周期不合法");
        }
    }
}
