package com.example.ai.optimize;

import com.example.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Embedding 去重缓存：同一内容 + 同一模型版本只应生成一次向量。
 *
 * <p>与 Milvus 解耦：这里只记录「内容指纹 → 已入库」的事实，
 * 真正的向量复用由 <code>DocumentIngestionService</code> 在启用 RAG 时调用本服务判断。
 * RAG 关闭时本服务仍可被统计接口查询，保证指标不为空。
 */
@Service
public class EmbeddingCacheService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCacheService.class);
    private static final String P = "ai:{cache}:embed:";
    private static final String SKIP_KEY = "ai:{cache}:stat:embed-skip";
    private static final String CALL_KEY = "ai:{cache}:stat:embed-call";

    private final StringRedisTemplate redis;
    private final AiProperties properties;

    public EmbeddingCacheService(StringRedisTemplate redis, AiProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 返回 true 表示该内容在相同模型版本下已生成过向量，可跳过。 */
    public boolean shouldSkip(String model, String content) {
        if (!properties.getOptimization().isEmbeddingDedupEnabled()
                || content == null || content.isBlank()) {
            return false;
        }
        try {
            boolean skip = Boolean.TRUE.equals(redis.hasKey(key(model, content)));
            redis.opsForValue().increment(skip ? SKIP_KEY : CALL_KEY);
            return skip;
        } catch (DataAccessException e) {
            log.warn("Embedding 去重检查失败，按需重新生成: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public void markEmbedded(String model, String content) {
        if (!properties.getOptimization().isEmbeddingDedupEnabled()
                || content == null || content.isBlank()) {
            return;
        }
        try {
            redis.opsForValue().set(key(model, content), "1", Duration.ofDays(30));
        } catch (DataAccessException e) {
            log.warn("写入 Embedding 去重标记失败: {}", e.getClass().getSimpleName());
        }
    }

    public Stats stats() {
        long skipped = readLong(SKIP_KEY);
        long called = readLong(CALL_KEY);
        double rate = skipped + called == 0 ? 0d : (double) skipped / (skipped + called);
        return new Stats(called, skipped, rate);
    }

    public record Stats(long generated, long skipped, double skipRate) {
    }

    String key(String model, String content) {
        return P + sha256((model == null ? "" : model) + "|" + content.trim().replaceAll("\\s+", " "));
    }

    private long readLong(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0L : Long.parseLong(v);
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
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
