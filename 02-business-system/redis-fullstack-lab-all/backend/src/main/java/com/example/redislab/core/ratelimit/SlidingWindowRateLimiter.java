package com.example.redislab.core.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

/**
 * 滑动窗口限流器（基于 ZSET）。
 *
 * <p>原理：
 * 每个请求用 score=当前毫秒时间戳、member=唯一 UUID 写入 ZSET；
 * 统计时先删除窗口外的记录（ZREMRANGEBYSCORE），再 ZCARD 计数。
 * 整个「清理 + 计数 + 写入」用一段 Lua 脚本在 Redis 单线程内原子完成，避免并发竞态。
 * 相比固定窗口，滑动窗口不会出现「窗口临界点双倍放行」的问题。</p>
 */
@Service
public class SlidingWindowRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> script;

    public SlidingWindowRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        // KEYS[1]=窗口key  ARGV[1]=nowMs  ARGV[2]=windowMs  ARGV[3]=limit  ARGV[4]=member
        String lua =
                "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]-ARGV[2]) " +
                "local cnt = redis.call('ZCARD', KEYS[1]) " +
                "if cnt >= tonumber(ARGV[3]) then return 0 end " +
                "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4]) " +
                "redis.call('PEXPIRE', KEYS[1], ARGV[2]) " +
                "return 1";
        this.script = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * @return true=放行，false=被限流拦截
     */
    public boolean tryAcquire(String key, int qps, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000L;
        String member = UUID.randomUUID().toString();
        Long result = stringRedisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(now), String.valueOf(windowMs), String.valueOf(qps), member);
        return result != null && result == 1L;
    }
}
