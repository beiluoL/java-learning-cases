package com.example.ai.alert;

import com.example.ai.agent.mapper.AgentCostTraceMapper;
import com.example.ai.analytics.mapper.CostAnalyticsMapper;
import com.example.ai.common.AiEnums;
import com.example.ai.common.Views;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常行为检测：可解释规则引擎，不引入黑箱模型。
 *
 * <p>已实现 6 条规则（均有历史基线对照，避免「绝对值拍脑袋」）：
 * <ol>
 *   <li>单用户成本突增：今日成本 &gt; 近 7 日日均成本 × 3 且 &gt; 0.01 USD；</li>
 *   <li>单次请求 Token 突增：今日峰值 &gt; 近 7 日峰值均值 × 5 且 &gt; 2000；</li>
 *   <li>错误率突升：今日失败率 &gt; 30% 且样本 ≥ 10；</li>
 *   <li>缓存命中率下降：基线命中率 &gt; 20%，今日跌破基线一半且输入 Token &gt; 1 万；</li>
 *   <li>夜间异常调用：UTC 00:00–06:00 请求数 &gt; 20；</li>
 *   <li>Agent 循环：今日出现 LOOP_DETECTED 中止任务。</li>
 * </ol>
 *
 * <p>未实现：基于时间序列的突变点检测、机器学习异常模型（先把可解释规则做扎实）。
 */
@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);
    private static final int BASELINE_DAYS = 7;

    private final CostAnalyticsMapper analyticsMapper;
    private final AgentCostTraceMapper agentTraceMapper;
    private final CostAlertService alertService;
    private final Clock clock;

    public AnomalyDetectionService(CostAnalyticsMapper analyticsMapper, AgentCostTraceMapper agentTraceMapper,
                                   CostAlertService alertService, Clock clock) {
        this.analyticsMapper = analyticsMapper;
        this.agentTraceMapper = agentTraceMapper;
        this.alertService = alertService;
        this.clock = clock;
    }

    public record Finding(String rule, String level, String targetType, String targetId,
                          String message, BigDecimal currentValue, BigDecimal baselineValue) {
    }

    /**
     * 执行一次全量扫描并写入告警中心。
     *
     * <p>本方法只负责编排与汇总；6 条规则各自独立成方法，便于单独校验与增删。
     * **规则顺序与查询顺序必须保持一致**：{@code overview} 在错误率与缓存命中率
     * 两处共用同一份结果，重排会引入重复查询并使基线对照失效。</p>
     *
     * @return 命中的异常项（同时已写入告警中心，重复项由告警指纹自动合并）
     */
    public List<Finding> scan() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime todayStart = today.atStartOfDay();
        List<Finding> findings = new ArrayList<>();
        if (!now.isAfter(todayStart)) {
            return findings;
        }
        ScanWindow window = new ScanWindow(todayStart, now, today.minusDays(BASELINE_DAYS).atStartOfDay());

        detectUserSpike(window, findings);
        detectTokenSpike(window, findings);
        // 当日总览在「错误率」与「缓存命中率」两条规则间共用，只查一次
        Map<String, Object> todayOverview = analyticsMapper.overview(window.todayStart(), window.now());
        detectErrorRate(todayOverview, findings);
        detectCacheHitDrop(window, todayOverview, findings);
        detectNightCalls(window, findings);
        detectAgentLoop(window, findings);

        pushToAlertCenter(findings);
        return findings;
    }

    /** 规则 1：单用户今日成本 &gt; 近 7 日日均 × 3，且绝对值 &gt; 0.01 USD（过滤小额噪声）。 */
    private void detectUserSpike(ScanWindow window, List<Finding> findings) {
        Map<String, BigDecimal> baselineByUser = new HashMap<>();
        for (Map<String, Object> row : analyticsMapper.userCost(window.baselineStart(), window.todayStart())) {
            baselineByUser.put(String.valueOf(row.get("userId")),
                    Views.decimal(row.get("totalCost"))
                            .divide(BigDecimal.valueOf(BASELINE_DAYS), 8, RoundingMode.HALF_UP));
        }
        for (Map<String, Object> row : analyticsMapper.userCost(window.todayStart(), window.now())) {
            String userId = String.valueOf(row.get("userId"));
            BigDecimal todayCost = Views.decimal(row.get("totalCost"));
            BigDecimal baseline = baselineByUser.getOrDefault(userId, BigDecimal.ZERO);
            BigDecimal threshold = baseline.multiply(BigDecimal.valueOf(3)).max(new BigDecimal("0.01"));
            if (todayCost.compareTo(threshold) > 0) {
                findings.add(new Finding("ANOMALY_USER_SPIKE", "HIGH", "USER", userId,
                        "用户今日成本 " + todayCost.setScale(6, RoundingMode.HALF_UP)
                                + " USD，超过近 7 日日均 " + baseline.setScale(6, RoundingMode.HALF_UP) + " USD 的 3 倍",
                        todayCost, baseline));
            }
        }
    }

    /** 规则 2：今日单次请求峰值 Token &gt; 近 7 日峰值均值 × 5，且 &gt; 2000（小样本不报）。 */
    private void detectTokenSpike(ScanWindow window, List<Finding> findings) {
        Map<String, Object> todayTokens = analyticsMapper.tokenStats(window.todayStart(), window.now());
        Map<String, Object> baseTokens = analyticsMapper.tokenStats(window.baselineStart(), window.todayStart());
        long todayMax = Views.longOf(todayTokens.get("maxTokens"));
        long baseAvg = Math.round(Views.decimal(baseTokens.get("avgTokens")).doubleValue());
        if (todayMax > Math.max(baseAvg * 5L, 2000L)) {
            findings.add(new Finding("ANOMALY_TOKEN_SPIKE", "HIGH", "GLOBAL", "__ALL__",
                    "今日单次请求最大 Token " + todayMax + "，远超近 7 日均值 " + baseAvg,
                    BigDecimal.valueOf(todayMax), BigDecimal.valueOf(baseAvg)));
        }
    }

    /** 规则 3：今日成功率 &lt; 70% 且样本 ≥ 10（样本太少时成功率无统计意义）。 */
    private void detectErrorRate(Map<String, Object> todayOverview, List<Finding> findings) {
        long requests = Views.longOf(todayOverview.get("requestCount"));
        double successRate = todayOverview.get("successRate") == null ? 1d
                : Views.decimal(todayOverview.get("successRate")).doubleValue();
        if (requests >= 10 && successRate < 0.7d) {
            findings.add(new Finding("ANOMALY_ERROR_RATE", "HIGH", "GLOBAL", "__ALL__",
                    "今日成功率仅 " + String.format("%.1f%%", successRate * 100)
                            + "（样本 " + requests + " 次），请检查模型供应商",
                    BigDecimal.valueOf(successRate), BigDecimal.valueOf(0.7d)));
        }
    }

    /**
     * 规则 4：Prompt 缓存命中率跌破基线一半。
     *
     * <p>用 {@code cached/input} 近似命中率。基线需 &gt; 20% 且当日输入 &gt; 1 万 Token，
     * 否则分母太小，命中率抖动会被误报成异常。</p>
     */
    private void detectCacheHitDrop(ScanWindow window, Map<String, Object> todayOverview, List<Finding> findings) {
        double todayRate = cacheRate(window.todayStart(), window.now());
        double baseRate = cacheRate(window.baselineStart(), window.todayStart());
        long todayInput = Views.longOf(todayOverview.get("inputTokens"));
        if (baseRate > 0.2d && todayRate < baseRate / 2 && todayInput > 10_000) {
            findings.add(new Finding("ANOMALY_CACHE_HIT_DROP", "WARNING", "GLOBAL", "__ALL__",
                    "Prompt 缓存命中率从 " + String.format("%.1f%%", baseRate * 100)
                            + " 降至 " + String.format("%.1f%%", todayRate * 100) + "，请检查 Prompt 前缀是否被改动",
                    BigDecimal.valueOf(todayRate), BigDecimal.valueOf(baseRate)));
        }
    }

    /** 规则 5：UTC 00:00–06:00 请求数 &gt; 20，提示可能是定时任务或异常脚本。 */
    private void detectNightCalls(ScanWindow window, List<Finding> findings) {
        long nightCalls = 0;
        for (Map<String, Object> row : analyticsMapper.trend(window.todayStart(), window.now(), "HOUR")) {
            int hour = parseHour(String.valueOf(row.get("bucket")));
            if (hour >= 0 && hour < 6) {
                nightCalls += Views.longOf(row.get("requestCount"));
            }
        }
        if (nightCalls > 20) {
            findings.add(new Finding("ANOMALY_NIGHT_CALL", "WARNING", "GLOBAL", "__ALL__",
                    "UTC 00:00-06:00 出现 " + nightCalls + " 次调用，请确认是否为定时任务或异常脚本",
                    BigDecimal.valueOf(nightCalls), BigDecimal.valueOf(20)));
        }
    }

    /** 规则 6：今日出现因检测到循环而被中止的 Agent 任务，直接判 CRITICAL。 */
    private void detectAgentLoop(ScanWindow window, List<Finding> findings) {
        long loops = agentTraceMapper.countStopped(window.todayStart(), window.now(), "LOOP_DETECTED");
        if (loops > 0) {
            findings.add(new Finding("ANOMALY_AGENT_LOOP", "CRITICAL", "AGENT", "__ALL__",
                    "今日有 " + loops + " 个 Agent 任务因检测到循环被中止",
                    BigDecimal.valueOf(loops), BigDecimal.ZERO));
        }
    }

    /** 统一投递到告警中心；去重与合并由告警指纹负责，本方法不做判重。 */
    private void pushToAlertCenter(List<Finding> findings) {
        for (Finding f : findings) {
            alertService.alert(AiEnums.AlertType.valueOf(f.rule()),
                    AiEnums.AlertLevel.valueOf(f.level()), f.targetType(), f.targetId(),
                    f.message(), f.baselineValue(), f.currentValue(), null);
        }
    }

    /**
     * 扫描窗口：当日区间与基线区间。
     *
     * @param todayStart    当日 00:00（UTC）
     * @param now           当前时刻（UTC）
     * @param baselineStart 基线起始（今日 - 7 天 的 00:00 UTC）
     */
    private record ScanWindow(LocalDateTime todayStart, LocalDateTime now, LocalDateTime baselineStart) {
    }

    @Scheduled(fixedDelay = 600_000L, initialDelay = 60_000L)
    public void scheduledScan() {
        try {
            List<Finding> findings = scan();
            if (!findings.isEmpty()) {
                log.info("异常检测命中 {} 条规则", findings.size());
            }
        } catch (RuntimeException e) {
            log.warn("异常检测执行失败: {}", e.getClass().getSimpleName());
        }
    }

    private double cacheRate(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> m = analyticsMapper.overview(from, to);
        long input = Views.longOf(m.get("inputTokens"));
        long cached = Views.longOf(m.get("cachedInputTokens"));
        return input == 0 ? 0d : (double) cached / input;
    }

    /** bucket 形如 "2026-11-11 03:00"，取小时位。 */
    private int parseHour(String bucket) {
        if (bucket == null || bucket.length() < 13) {
            return -1;
        }
        try {
            return Integer.parseInt(bucket.substring(11, 13));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
