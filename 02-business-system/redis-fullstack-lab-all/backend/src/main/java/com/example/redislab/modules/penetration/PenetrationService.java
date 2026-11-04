package com.example.redislab.modules.penetration;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import com.example.redislab.core.bloom.BloomFilterService;
import com.example.redislab.entity.Product;
import com.example.redislab.mapper.ProductMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景6：缓存穿透防护。
 *
 * <p>缓存穿透 = 查询「根本不存在的数据」，缓存和 DB 都没有，请求每次都打到 DB（恶意刷 -1、超长随机串）。
 * 两道防线：
 *  1) 布隆过滤器：预热所有合法 id，不存在的 id 直接拦截，绝不查 DB；
 *  2) 空值缓存：布隆说「可能存在」但 DB 真没有时，缓存一个空标记（短 TTL），避免同一不存在的 key 反复回源。</p>
 */
@Service
public class PenetrationService {

    private static final String NULL_MARK = "__NULL__";

    private final BloomFilterService bloomFilterService;
    private final ProductMapper productMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final StatService statService;

    public PenetrationService(BloomFilterService bloomFilterService,
                             ProductMapper productMapper,
                             StringRedisTemplate stringRedisTemplate,
                             ObjectMapper objectMapper,
                             StatService statService) {
        this.bloomFilterService = bloomFilterService;
        this.productMapper = productMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.statService = statService;
    }

    public Product getProduct(Long id) {
        statService.incr("penetration:req");

        // 第一道：布隆过滤器。不存在 = 一定不存在，直接拦截。误判率内可能放过，但绝不会「漏拦真不存在的」。
        if (!bloomFilterService.mightContain(id)) {
            statService.incr("penetration:bloom_block");
            return null;
        }

        String key = RedisKeys.penetrationProduct(id);
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARK.equals(cached)) {
                statService.incr("penetration:nullcache_hit");
                return null; // 命中空缓存，直接返回，不再查 DB
            }
            try {
                statService.incr("penetration:cache_hit");
                return objectMapper.readValue(cached, Product.class);
            } catch (Exception e) {
                // 解析失败按未命中处理
            }
        }

        // 布隆说可能存在 -> 查 DB
        statService.incr("penetration:db");
        Product product = productMapper.selectById(id);
        if (product == null) {
            // 第二道：缓存空对象，短 TTL（60s），防同一非法 key 重复穿透
            stringRedisTemplate.opsForValue().set(key, NULL_MARK, Duration.ofSeconds(60));
            statService.incr("penetration:nullcache_set");
            return null;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product), Duration.ofSeconds(300));
        } catch (Exception ignored) {
        }
        return product;
    }

    /** 模拟攻击：批量打一堆非法（负数）id，统计布隆拦截情况 */
    public Map<String, Object> attack(int count) {
        for (int i = 0; i < count; i++) {
            getProduct(-1L * (i + 1)); // 负数 id 一定不在布隆里，应被全部拦截
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", count);
        summary.put("bloomBlocked", statService.get("penetration:bloom_block"));
        summary.put("dbHits", statService.get("penetration:db"));
        summary.put("note", "被布隆过滤器拦截的请求不会落到 DB（dbHits 应远小于 total）");
        return summary;
    }
}
