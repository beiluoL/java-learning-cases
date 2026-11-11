package com.example.ai.optimization;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prompt 优化器：只做<b>无损</b>优化，不做语义删减。
 *
 * <p>已实现的三类变换：
 * <ol>
 *   <li>空白归一：去除行尾空白、压缩连续空行；</li>
 *   <li>行级去重：完全相同的指令行只保留第一次出现；</li>
 *   <li>固定前缀：把稳定的系统提示词放在最前面，便于供应商 Prompt Caching 命中。</li>
 * </ol>
 *
 * <p><b>不做</b>的事情：不以「精简」为名删除可能影响回答质量的约束条款。
 * 任何语义级删减都必须先通过 Golden Dataset 质量评测，本项目未做该评测，因此不实现。
 */
@Component
public class PromptOptimizer {

    /** 固定前缀：内容稳定，供应商侧可据此命中 prompt cache。改动必须同步 ai.cache.prompt-version。 */
    public static final String STABLE_PREFIX =
            "你是企业级 AI 助手。回答要求：优先依据给定参考资料；资料不足时明确说明；不编造事实；使用简洁中文。";

    public record Result(String before, String after, int beforeTokens, int afterTokens,
                         int savedTokens, List<String> appliedRules) {
        public boolean changed() {
            return savedTokens > 0;
        }
    }

    private final TokenEstimator estimator;

    public PromptOptimizer(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    public Result optimize(String prompt) {
        List<String> rules = new ArrayList<>();
        String before = prompt == null ? "" : prompt;
        if (before.isBlank()) {
            return new Result(before, before, 0, 0, 0, rules);
        }

        String normalized = before.replace("\r\n", "\n").replaceAll("[ \t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n").trim();
        if (!normalized.equals(before)) {
            rules.add("WHITESPACE_NORMALIZE");
        }

        // 行级去重：保持原有顺序，仅去掉重复指令
        String[] lines = normalized.split("\n", -1);
        Set<String> seen = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>(lines.length);
        int duplicates = 0;
        for (String line : lines) {
            String key = line.trim();
            if (key.isEmpty()) {
                kept.add("");
                continue;
            }
            if (!seen.add(key)) {
                duplicates++;
                continue;
            }
            kept.add(line);
        }
        String deduped = String.join("\n", kept).replaceAll("\n{3,}", "\n\n").trim();
        if (duplicates > 0) {
            rules.add("LINE_DEDUP x" + duplicates);
        }

        int beforeTokens = estimator.estimate(before);
        int afterTokens = estimator.estimate(deduped);
        return new Result(before, deduped, beforeTokens, afterTokens,
                Math.max(beforeTokens - afterTokens, 0), rules);
    }

    /** 组装稳定前缀 + 场景补充，动态内容（用户问题、检索片段）必须放在后面。 */
    public String composeSystemPrompt(String sceneInstruction) {
        if (sceneInstruction == null || sceneInstruction.isBlank()) {
            return STABLE_PREFIX;
        }
        return STABLE_PREFIX + "\n" + sceneInstruction.trim();
    }

    /** 供前端/文档展示的规则清单，避免「优化了什么」说不清。 */
    public Map<String, String> describeRules() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("WHITESPACE_NORMALIZE", "归一空白与连续空行，不改变语义");
        m.put("LINE_DEDUP", "删除完全重复的指令行，保留首次出现");
        m.put("STABLE_PREFIX", "固定系统前缀置于最前，便于 Provider Prompt Caching 命中");
        return m;
    }
}
