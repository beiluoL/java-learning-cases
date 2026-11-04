package com.example.redislab.modules.ratelimit;

import com.example.redislab.common.R;
import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import com.example.redislab.core.ratelimit.RateLimit;
import com.example.redislab.core.ratelimit.TokenBucketRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 场景4：限流控制。
 * GET /api/ratelimit/order  被 @RateLimit 限流（滑动窗口，QPS=5，演示并发打会被拦截）
 * GET /api/ratelimit/probe  不限流的对照组
 * GET /api/ratelimit/fixed  固定窗口（INCR+EXPIRE）实现，用于对比
 * GET /api/ratelimit/token  令牌桶实现，用于对比
 */
@RestController
@RequestMapping("/api/ratelimit")
public class RateLimitController {

    private final StringRedisTemplate stringRedisTemplate;
    private final TokenBucketRateLimiter tokenBucket;
    private final StatService statService;

    public RateLimitController(StringRedisTemplate stringRedisTemplate,
                              TokenBucketRateLimiter tokenBucket,
                              StatService statService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenBucket = tokenBucket;
        this.statService = statService;
    }

    @RateLimit(key = "order", qps = 5, windowSeconds = 1)
    @GetMapping("/order")
    public R<String> order() {
        statService.incr("ratelimit:pass");
        return R.ok("下单成功（本请求在限流窗口内被放行）");
    }

    @GetMapping("/probe")
    public R<String> probe() {
        return R.ok("探测接口，不受限流");
    }

    /** 固定窗口：INCR 计数 + 首条设 EXPIRE，窗口内超阈值即认为需拦截（这里仅统计，业务自行判断） */
    @GetMapping("/fixed")
    public R<Map<String, Object>> fixed(@RequestParam(defaultValue = "demo") String key) {
        String redisKey = RedisKeys.ratelimitFixed(key);
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(redisKey, Duration.ofSeconds(10));
        }
        statService.incr("ratelimit:fixed:" + key);
        Map<String, Object> map = new HashMap<>();
        map.put("count", count);
        map.put("window", "10s");
        map.put("note", "固定窗口统计；与滑动窗口相比，窗口临界点可能出现「双倍放行」");
        return R.ok(map);
    }

    /** 令牌桶：rate=5/s，容量=10 */
    @GetMapping("/token")
    public R<Map<String, Object>> token(@RequestParam(defaultValue = "demo") String key) {
        boolean allowed = tokenBucket.tryAcquire(RedisKeys.ratelimitWindow("token:" + key), 5.0, 10);
        Map<String, Object> map = new HashMap<>();
        map.put("allowed", allowed);
        map.put("algorithm", "token-bucket");
        return R.ok(map);
    }
}
