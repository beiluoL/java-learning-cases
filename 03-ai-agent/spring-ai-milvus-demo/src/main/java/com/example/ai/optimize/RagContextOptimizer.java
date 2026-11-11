package com.example.ai.optimize;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RAG 检索上下文优化：去重 + 过滤碎片 + 按 Token 预算截断。
 *
 * <p>输入是 {@code DocumentIngestionService} 拼装出的上下文字符串，
 * 因此这里只做与检索实现解耦的通用优化，不假装能做 Hybrid Search 或 Rerank
 * （本项目 RAG 默认关闭，Milvus 未启动时不会走到这条链路）。
 */
@Component
public class RagContextOptimizer {

    private static final int MIN_FRAGMENT_CHARS = 24;

    public record Result(String context, int fragmentCountBefore, int fragmentCountAfter,
                         int beforeTokens, int afterTokens, int savedTokens, String note) {
    }

    private final TokenEstimator estimator;

    public RagContextOptimizer(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    public Result optimize(String context, int maxTokens) {
        if (context == null || context.isBlank()) {
            return new Result("", 0, 0, 0, 0, 0, "无检索上下文");
        }
        String[] raw = context.split("\n{2,}|\\n---\\n");
        List<String> fragments = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int before = 0;
        for (String part : raw) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            before++;
            if (t.length() < MIN_FRAGMENT_CHARS) {
                continue;
            }
            String key = sha256(t.replaceAll("\\s+", ""));
            if (!seen.add(key)) {
                continue;
            }
            fragments.add(t);
        }

        StringBuilder sb = new StringBuilder();
        int used = 0;
        int kept = 0;
        for (String f : fragments) {
            int t = estimator.estimate(f);
            if (used + t > maxTokens && kept > 0) {
                break;
            }
            sb.append(f).append("\n\n");
            used += t;
            kept++;
        }
        String out = sb.toString().trim();
        int beforeTokens = estimator.estimate(context);
        return new Result(out, before, kept, beforeTokens, used,
                Math.max(beforeTokens - used, 0),
                "去重与碎片过滤完成；相似度阈值与 Rerank 未启用（RAG 关闭）");
    }

    static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
