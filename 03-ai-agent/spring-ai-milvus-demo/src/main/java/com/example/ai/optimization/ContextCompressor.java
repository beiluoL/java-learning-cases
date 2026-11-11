package com.example.ai.optimization;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文压缩器：滑动窗口 + 重复消息去重 + 空白过滤。
 *
 * <p>策略：保留最近 N 轮完整对话，较早轮次被丢弃并在开头插入一条「已省略」提示。
 * 这里<b>不</b>做 LLM 摘要（摘要本身要花 Token，且会引入不可控的信息损失），
 * 因此质量影响未评测，调用方必须把 qualityNote 如实写进优化记录。
 */
@Component
public class ContextCompressor {

    /** 与 Spring AI Message 解耦的中立轮次结构，便于单测与复用。 */
    public record Turn(String role, String text) {
    }

    public record Result(List<Turn> turns, int droppedTurns, int dedupedTurns,
                         int beforeTokens, int afterTokens, int savedTokens) {
    }

    private final TokenEstimator estimator;

    public ContextCompressor(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    /**
     * @param turns            完整历史（按时间升序）
     * @param keepRecentRounds 保留最近多少轮（一轮 = 一条 user + 其后的 assistant）
     * @param maxTokens        压缩后允许的最大估算 Token
     */
    public Result compress(List<Turn> turns, int keepRecentRounds, int maxTokens) {
        List<Turn> safe = turns == null ? List.of() : turns;
        int beforeTokens = safe.stream().mapToInt(t -> estimator.estimate(t.text())).sum();

        Set<String> seen = new HashSet<>();
        List<Turn> deduped = new ArrayList<>(safe.size());
        int dedupCount = 0;
        for (Turn t : safe) {
            if (t == null || t.text() == null || t.text().isBlank()) {
                continue;
            }
            String key = (t.role() == null ? "" : t.role()) + "\u0000" + t.text().trim();
            if (!seen.add(key)) {
                dedupCount++;
                continue;
            }
            deduped.add(t);
        }

        int keep = Math.max(keepRecentRounds, 1) * 2;
        int from = Math.max(deduped.size() - keep, 0);
        int dropped = from;
        List<Turn> window = new ArrayList<>(deduped.subList(from, deduped.size()));

        // Token 上限：从最早的消息开始丢，直到满足预算
        while (window.size() > 1 && window.stream().mapToInt(t -> estimator.estimate(t.text())).sum() > maxTokens) {
            window.remove(0);
            dropped++;
        }

        List<Turn> result = new ArrayList<>(window.size() + 1);
        if (dropped > 0) {
            result.add(new Turn("system", "【上下文已压缩】较早的 " + dropped + " 条消息已省略。"));
        }
        result.addAll(window);

        int afterTokens = result.stream().mapToInt(t -> estimator.estimate(t.text())).sum();
        return new Result(result, dropped, dedupCount, beforeTokens, afterTokens,
                Math.max(beforeTokens - afterTokens, 0));
    }
}
