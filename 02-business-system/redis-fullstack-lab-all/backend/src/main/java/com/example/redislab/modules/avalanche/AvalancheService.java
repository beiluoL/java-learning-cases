package com.example.redislab.modules.avalanche;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import com.example.redislab.entity.Product;
import com.example.redislab.mapper.ProductMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 场景8：缓存雪崩防护。
 *
 * <p>缓存雪崩 = 大量 key 在同一时刻集体失效（或 Redis 宕机），瞬时请求全压到 DB。
 * 防护组合拳：
 *  1) TTL 随机抖动：每个 key 的过期时间 = 基础值 + 随机增量，打散失效时刻（见 RedisKeys.ttlWithJitter）；
 *  2) 多级缓存：本地 Caffeine 一级 + Redis 二级，扛住瞬时洪峰，绝大多数请求不触达 Redis；
 *  3) 熔断降级：Redis 不可用时（Resilience4j 熔断），直接返回兜底数据，保护 DB 不被冲垮。</p>
 */
@Service
public class AvalancheService {

    // 一级缓存：本地 Caffeine，进程内纳秒级读取
    private final Cache<Long, Product> localCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductMapper productMapper;
    private final StatService statService;
    private volatile boolean redisDown = false;

    public AvalancheService(RedisTemplate<String, Object> redisTemplate,
                           ProductMapper productMapper,
                           StatService statService) {
        this.redisTemplate = redisTemplate;
        this.productMapper = productMapper;
        this.statService = statService;
    }

    /** 带防护的读取：本地 → Redis → DB，Redis 异常时熔断降级 */
    @CircuitBreaker(name = "redisAvalanche", fallbackMethod = "fallbackProduct")
    public Product loadProtected(Long id) {
        Product p = localCache.getIfPresent(id);
        if (p != null) {
            statService.incr("avalanche:l1hit");
            return p;
        }
        if (redisDown) {
            throw new RuntimeException("redis unavailable");
        }
        Product r = (Product) redisTemplate.opsForValue().get(RedisKeys.avalancheProduct(id));
        if (r != null) {
            statService.incr("avalanche:l2hit");
            localCache.put(id, r);
            return r;
        }
        // 回源 DB
        statService.incr("avalanche:db");
        Product db = productMapper.selectById(id);
        if (db != null) {
            redisTemplate.opsForValue().set(RedisKeys.avalancheProduct(id), db,
                    Duration.ofSeconds(RedisKeys.ttlWithJitter(300, 60)));
            localCache.put(id, db);
        }
        return db;
    }

    /** 无防护读取：固定 TTL，且不做熔断——用于对比「雪崩」时 DB 被打穿 */
    public Product loadUnprotected(Long id) {
        Product r = (Product) redisTemplate.opsForValue().get(RedisKeys.avalancheProduct(id));
        if (r != null) {
            statService.incr("avalanche:l2hit");
            return r;
        }
        statService.incr("avalanche:db"); // 雪崩时大量请求同时到这里
        return productMapper.selectById(id);
    }

    /** 熔断降级兜底：返回兜底数据，DB 不被冲垮 */
    public Product fallbackProduct(Long id, Throwable t) {
        statService.incr("avalanche:fallback");
        Product fb = new Product();
        fb.setId(id);
        fb.setName("降级兜底数据（Redis 不可用，已熔断）");
        fb.setPrice(BigDecimal.ZERO);
        return fb;
    }

    /** 预热：protect=true 用随机 TTL（打散失效），false 用固定 TTL（同时失效→雪崩） */
    public void preheat(boolean protect) {
        for (long id = 1; id <= 3; id++) {
            Product db = productMapper.selectById(id);
            if (db == null) {
                continue;
            }
            long ttl = protect ? RedisKeys.ttlWithJitter(300, 60) : 30;
            redisTemplate.opsForValue().set(RedisKeys.avalancheProduct(id), db, Duration.ofSeconds(ttl));
        }
    }

    public void setRedisDown(boolean down) {
        this.redisDown = down;
    }
}
