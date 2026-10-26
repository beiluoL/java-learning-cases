package com.example.csagent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 满意度统计。 */
@Data
public class SatisfactionStatsDTO {
    private long total;
    private double average;
    /** 1-5 分分布：key=分数, value=数量 */
    private Map<Integer, Long> distribution;
    /** 按意图分布的满意度：[{intent, count, average}] */
    private List<IntentStat> byIntent;
    /** 转人工会话数 / 总会话数 */
    private long escalatedConversations;
    private long totalConversations;

    @Data
    public static class IntentStat {
        private String intent;
        private long count;
        private double average;
    }
}
