package com.iot.bigdata.opinion.analyzer;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 情感分析器（基于情感词典的轻量实现，适合学习演示）。
 *
 * <p>原理：统计文本中正/负向情感词出现次数，得分 = (正向 - 负向) / (正向 + 负向 + 1)，
 * 归一到 [-1, 1]；再按阈值判定 POSITIVE / NEGATIVE / NEUTRAL。
 * 生产环境可替换为模型服务（如情感分类模型 / 大模型）。</p>
 */
@Component
public class SentimentAnalyzer {

    private static final List<String> POSITIVE_WORDS = Arrays.asList(
            "好", "优秀", "满意", "喜欢", "棒", "推荐", "快速", "稳定", "赞", "给力",
            "正品", "便宜", "方便", "惊喜", "靠谱", "高效", "流畅", "清晰", "贴心", "超值");

    private static final List<String> NEGATIVE_WORDS = Arrays.asList(
            "差", "差评", "垃圾", "慢", "坏", "故障", "投诉", "失望", "贵", "卡",
            "崩溃", "虚假", "骗", "问题", "缩水", "劣质", "延迟", "断连", "发热", "吐槽");

    /**
     * 分析文本情感。
     *
     * @param text 待分析文本
     * @return 情感结果（得分与倾向）
     */
    public SentimentResult analyze(String text) {
        if (text == null || text.isEmpty()) {
            return new SentimentResult(0.0, "NEUTRAL");
        }
        int pos = countMatches(text, POSITIVE_WORDS);
        int neg = countMatches(text, NEGATIVE_WORDS);
        double score = (pos - neg) / (double) (pos + neg + 1);
        String sentiment;
        if (score > 0.15) {
            sentiment = "POSITIVE";
        } else if (score < -0.15) {
            sentiment = "NEGATIVE";
        } else {
            sentiment = "NEUTRAL";
        }
        return new SentimentResult(Math.round(score * 1000.0) / 1000.0, sentiment);
    }

    private int countMatches(String text, List<String> words) {
        int count = 0;
        for (String word : words) {
            int idx = 0;
            while ((idx = text.indexOf(word, idx)) != -1) {
                count++;
                idx += word.length();
            }
        }
        return count;
    }

    /** 情感分析结果。 */
    public static class SentimentResult {
        private final double score;
        private final String sentiment;

        public SentimentResult(double score, String sentiment) {
            this.score = score;
            this.sentiment = sentiment;
        }

        public double getScore() {
            return score;
        }

        public String getSentiment() {
            return sentiment;
        }
    }
}
