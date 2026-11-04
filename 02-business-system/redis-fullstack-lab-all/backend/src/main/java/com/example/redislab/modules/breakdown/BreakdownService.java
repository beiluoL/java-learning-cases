package com.example.redislab.modules.breakdown;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import com.example.redislab.entity.Product;
import com.example.redislab.mapper.ProductMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 场景7：缓存击穿防护。
 *
 * <p>缓存击穿 ≠ 穿透：击穿是「某个爆款热点 key 过期瞬间」，海量并发同时发现缓存没了，
 * 一起回源 DB，把 DB 打挂。核心解法是「只允许一个线程去重建缓存」。</p>
 *
 * <p>两种方案：
 *  A) 互斥锁重建：未命中时抢分布式锁，抢到的回源，其余自旋等待——简单直接，但重建期间请求被阻塞。
 *  B) 逻辑过期：value 内嵌过期时间，不真过期；发现逻辑过期后异步线程重建，当前请求返回旧值——非阻塞，体验更好。</p>
 */
@Service
public class BreakdownService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductMapper productMapper;
    private final StatService statService;
    private final ExecutorService async = Executors.newCachedThreadPool();

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    public BreakdownService(RedisTemplate<String, Object> redisTemplate,
                           StringRedisTemplate stringRedisTemplate,
                           ProductMapper productMapper,
                           StatService statService) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.productMapper = productMapper;
        this.statService = statService;
    }

    /** 方案A：互斥锁重建 */
    public Product getHotMutex(Long id) {
        String key = RedisKeys.breakdownHot(id);
        Product cached = (Product) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            statService.incr("breakdown:mutex:hit");
            return cached;
        }
        // 缓存失效，抢锁重建（同一 key 同一时刻只有一个线程能抢到）
        String lockKey = RedisKeys.lockMutex("hot:" + id);
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(10));
        if (Boolean.TRUE.equals(locked)) {
            try {
                statService.incr("breakdown:mutex:rebuild"); // 关键指标：并发下应≈1
                Product db = loadFromDb(id);
                redisTemplate.opsForValue().set(key, db, Duration.ofSeconds(300));
                return db;
            } finally {
                stringRedisTemplate.execute(new DefaultRedisScript<>(UNLOCK_LUA, Long.class),
                        Collections.singletonList(lockKey), token);
            }
        } else {
            // 没抢到锁：自旋短暂等待缓存被重建，再读
            statService.incr("breakdown:mutex:wait");
            for (int i = 0; i < 20; i++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                Product v = (Product) redisTemplate.opsForValue().get(key);
                if (v != null) {
                    return v;
                }
            }
            return loadFromDb(id); // 兜底
        }
    }

    /** 方案B：逻辑过期（非阻塞） */
    public Product getHotLogical(Long id) {
        String key = RedisKeys.breakdownHot(id) + ":logic";
        LogicalValue lv = (LogicalValue) redisTemplate.opsForValue().get(key);
        if (lv != null) {
            statService.incr("breakdown:logic:hit");
            if (System.currentTimeMillis() < lv.expireAt) {
                return lv.value;
            }
            // 逻辑过期：异步重建，本轮直接返回旧值（不阻塞用户）
            statService.incr("breakdown:logic:rebuild_async");
            async.submit(() -> {
                Product db = loadFromDb(id);
                redisTemplate.opsForValue().set(key, new LogicalValue(db, System.currentTimeMillis() + 300_000L));
            });
            return lv.value;
        }
        // 完全没有：直接重建
        statService.incr("breakdown:logic:rebuild");
        Product db = loadFromDb(id);
        redisTemplate.opsForValue().set(key, new LogicalValue(db, System.currentTimeMillis() + 300_000L));
        return db;
    }

    /** 让热点 key「过期」，触发并发重建（演示击穿） */
    public void forceExpireMutex(Long id) {
        redisTemplate.delete(RedisKeys.breakdownHot(id));
    }

    public void forceExpireLogical(Long id) {
        String key = RedisKeys.breakdownHot(id) + ":logic";
        LogicalValue lv = (LogicalValue) redisTemplate.opsForValue().get(key);
        if (lv != null) {
            lv.expireAt = 1L; // 置为已过期
            redisTemplate.opsForValue().set(key, lv);
        }
    }

    private Product loadFromDb(Long id) {
        return productMapper.selectById(id);
    }

    /** 逻辑过期包装：value + 逻辑过期时间戳 */
    public static class LogicalValue {
        public Product value;
        public long expireAt;

        public LogicalValue() {
        }

        public LogicalValue(Product value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
