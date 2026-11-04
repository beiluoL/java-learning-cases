package com.example.redislab.core.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 令牌桶限流（Lua 实现，与滑动窗口并列作为限流方案对比）。
 *
 * <p>思路：Redis 里维护一个「桶」，按固定速率投放令牌；请求来时若桶里有令牌就放行并扣 1，
 * 否则拒绝。相比滑动窗口，令牌桶能容忍瞬时突发（桶里积攒的令牌），更适合「平时低频、偶发尖峰」的接口。</p>
 */
@Service
public class TokenBucketRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> script;

    public TokenBucketRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        String lua = """
                local key = KEYS[1]
                local now = tonumber(ARGV[1])
                local rate = tonumber(ARGV[2])
                local capacity = tonumber(ARGV[3])
                local requested = tonumber(ARGV[4])
                local data = redis.call('hmget', key, 'tokens', 'ts')
                local tokens = tonumber(data[1])
                local ts = tonumber(data[2])
                if tokens == nil then
                  tokens = capacity
                  ts = now
                end
                local delta = math.min(capacity - tokens, (now - ts) * rate / 1000)
                tokens = tokens + delta
                ts = now
                local allowed = 0
                if tokens >= requested then
                  tokens = tokens - requested
                  allowed = 1
                end
                redis.call('hmset', key, 'tokens', tokens, 'ts', ts)
                redis.call('expire', key, 60)
                return allowed
                """;
        this.script = new DefaultRedisScript<>(lua, Long.class);
    }

    /**
     * @return true=放行，false=桶空被拒
     */
    public boolean tryAcquire(String key, double ratePerSecond, int capacity) {
        long now = System.currentTimeMillis();
        Long result = stringRedisTemplate.execute(script,
                Collections.singletonList(key),
                String.valueOf(now), String.valueOf(ratePerSecond), String.valueOf(capacity), "1");
        return result != null && result == 1L;
    }
}
