package com.example.ai.optimize;

import com.example.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/**
 * 响应缓存（精确匹配）：命中即完全跳过模型调用，是最直接的降本手段。
 *
 * <p>Key 组成包含 用户 + 知识库 + 模型 + 提示词版本 + 归一化后的 Prompt，
 * 目的是避免跨租户/跨模型/跨模板串答案。语义缓存（向量相似）本项目未实现，
 * 因为缺少可评测的阈值与误命中保护，宁可不做也不引入静默错误。
 */
@Service
public class ResponseCacheService {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheService.class);
    private static final String P = "ai:{cache}:resp:";
    private static final String HIT_KEY = "ai:{cache}:stat:hit";
    private static final String MISS_KEY = "ai:{cache}:stat:miss";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiProperties properties;

    public ResponseCacheService(StringRedisTemplate redis, ObjectMapper objectMapper, AiProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 缓存条目：保存上次真实调用的 Usage 与成本快照，用于命中时如实记账（成本 0、节省 = 原成本）。 */
    public record CachedAnswer(String answer, String model, String provider, String routeLevel,
                               long inputTokens, long outputTokens, long cachedInputTokens,
                               long reasoningTokens, String totalCost, String pricingVersion,
                               long storedAtMillis) {
    }

    public String key(String userId, String knowledgeBaseId, String model, String prompt) {
        String raw = (userId == null ? "" : userId) + "|"
                + (knowledgeBaseId == null ? "" : knowledgeBaseId) + "|"
                + (model == null ? "" : model) + "|"
                + properties.getCache().getPromptVersion() + "|"
                + (prompt == null ? "" : prompt.trim().replaceAll("\\s+", " "));
        return P + sha256(raw);
    }

    public Optional<CachedAnswer> lookup(String key) {
        if (!properties.getCache().isEnabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                redis.opsForValue().increment(MISS_KEY);
                return Optional.empty();
            }
            redis.opsForValue().increment(HIT_KEY);
            return Optional.of(objectMapper.readValue(json, CachedAnswer.class));
        } catch (DataAccessException e) {
            log.warn("读取响应缓存失败，按未命中处理: {}", e.getClass().getSimpleName());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("反序列化缓存失败，按未命中处理: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void store(String key, CachedAnswer answer) {
        if (!properties.getCache().isEnabled()) {
            return;
        }
        if (answer.answer() == null || answer.answer().length() > properties.getCache().getMaxAnswerChars()) {
            return;
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(answer),
                    Duration.ofSeconds(properties.getCache().getTtlSeconds()));
        } catch (DataAccessException e) {
            log.warn("写入响应缓存失败: {}", e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("序列化缓存失败: {}", e.getClass().getSimpleName());
        }
    }

    /** 命中/未命中计数，供优化中心展示真实缓存效果。 */
    public CacheStats stats() {
        long hit = readLong(HIT_KEY);
        long miss = readLong(MISS_KEY);
        double rate = hit + miss == 0 ? 0d : (double) hit / (hit + miss);
        return new CacheStats(hit, miss, rate);
    }

    public record CacheStats(long hit, long miss, double hitRate) {
    }

    public void resetStats() {
        redis.delete(HIT_KEY);
        redis.delete(MISS_KEY);
    }

    /** 缓存节省成本只在命中时按上次真实成本累加，避免凭空编造「节省」。 */
    public BigDecimal savedCostOf(CachedAnswer cached) {
        return cached == null || cached.totalCost() == null ? BigDecimal.ZERO : new BigDecimal(cached.totalCost());
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
