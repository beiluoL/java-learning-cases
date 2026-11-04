package com.example.mq.service;

import com.example.mq.config.MqProperties;
import com.example.mq.entity.MqProduct;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品详情缓存（场景三的 Redis 侧）。
 * <p>
 * 缓存里额外存一个 {@code version}，用于「防乱序」：
 * 商品被连续改价两次，消息在 MQ 里可能乱序到达，消费者据此丢弃旧版本，
 * 保证缓存最终一定收敛到最新值，而不是被一条迟到消息打回旧价。
 */
@Slf4j
@Service
public class ProductCacheService {

    private static final String KEY_PREFIX = "product:detail:";
    private static final String VERSION_FIELD = "version";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MqProperties properties;

    public ProductCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MqProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 写缓存（上架/改价/信息更新） */
    public void write(MqProduct product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", product.getId());
        payload.put("productNo", product.getProductNo());
        payload.put("name", product.getName());
        payload.put("price", product.getPrice());
        payload.put("status", product.getStatus());
        payload.put(VERSION_FIELD, product.getVersion());
        payload.put("cachedAt", LocalDateTime.now().toString());
        try {
            redisTemplate.opsForValue().set(key(product.getId()),
                    objectMapper.writeValueAsString(payload),
                    Duration.ofMinutes(properties.getProductCacheTtlMinutes()));
        } catch (Exception e) {
            throw new IllegalStateException("写商品缓存失败 productId=" + product.getId(), e);
        }
    }

    /** 删缓存（下架）：下架商品不应继续出现在缓存里被读到 */
    public void evict(Long productId) {
        redisTemplate.delete(key(productId));
    }

    public String readRaw(Long productId) {
        try {
            return redisTemplate.opsForValue().get(key(productId));
        } catch (Exception e) {
            log.warn("[CACHE] 读取缓存失败 productId={}：{}", productId, e.getMessage());
            return null;
        }
    }

    public Long ttlSeconds(Long productId) {
        try {
            return redisTemplate.getExpire(key(productId));
        } catch (Exception e) {
            return null;
        }
    }

    /** 读取缓存中已有的版本号，null 表示无缓存或解析失败 */
    public Integer cachedVersion(Long productId) {
        String raw = readRaw(productId);
        if (raw == null) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(raw, Map.class);
            Object version = map.get(VERSION_FIELD);
            return version == null ? null : Integer.valueOf(String.valueOf(version));
        } catch (Exception e) {
            return null;
        }
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
