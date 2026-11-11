package com.example.ai.token.analytics.service;

import com.example.ai.token.analytics.entity.DailyCostSummary;
import com.example.ai.token.analytics.mapper.DailyCostSummaryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日成本汇总：把明细账聚合到日粒度，供趋势/同比使用。
 *
 * <p>重建逻辑全部在 SQL（INSERT ... SELECT + ON DUPLICATE KEY），保证可重跑、幂等、
 * 且不会因为服务重启丢数据。定时任务只重建「今天 + 昨天」，
 * 历史日期可通过管理接口按需重建。
 */
@Service
public class DailySummaryService {

    private static final Logger log = LoggerFactory.getLogger(DailySummaryService.class);

    private final DailyCostSummaryMapper mapper;
    private final Clock clock;

    public DailySummaryService(DailyCostSummaryMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 重建指定日期全部维度，返回各维度写入行数。 */
    public Map<String, Integer> rebuild(LocalDate day) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("ALL", mapper.rebuildAll(day));
        result.put("MODEL", mapper.rebuildByModel(day));
        result.put("SCENE", mapper.rebuildByScene(day));
        result.put("USER", mapper.rebuildByUser(day));
        result.put("AGENT", mapper.rebuildByAgent(day));
        return result;
    }

    public List<DailyCostSummary> list(String dimension, LocalDate from, LocalDate to) {
        String dim = dimension == null || dimension.isBlank() ? "ALL" : dimension.toUpperCase();
        return new ArrayList<>(mapper.listByRange(dim, from, to));
    }

    /** 每小时第 5 分钟重建今天与昨天，避免当日数据在 Dashboard 上缺失。 */
    @Scheduled(cron = "0 5 * * * *")
    public void rebuildRecent() {
        try {
            LocalDate today = LocalDate.now(clock);
            rebuild(today);
            rebuild(today.minusDays(1));
        } catch (RuntimeException e) {
            log.warn("每日汇总重建失败: {}", e.getClass().getSimpleName());
        }
    }
}
