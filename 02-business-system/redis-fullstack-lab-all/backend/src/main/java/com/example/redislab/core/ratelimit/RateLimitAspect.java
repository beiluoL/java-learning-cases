package com.example.redislab.core.ratelimit;

import com.example.redislab.common.RateLimitException;
import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 限流切面：拦截 @RateLimit 注解方法，调用滑动窗口限流器。
 * 被拦截时抛 RateLimitException，由全局异常处理器映射为 429，并累计「被拦截」计数供前端观测。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final SlidingWindowRateLimiter limiter;
    private final StatService statService;

    public RateLimitAspect(SlidingWindowRateLimiter limiter, StatService statService) {
        this.limiter = limiter;
        this.statService = statService;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = StringUtils.hasText(rateLimit.key())
                ? rateLimit.key()
                : pjp.getSignature().getDeclaringType().getSimpleName() + ":" + pjp.getSignature().getName();
        String redisKey = RedisKeys.ratelimitWindow(key);
        boolean allowed = limiter.tryAcquire(redisKey, rateLimit.qps(), rateLimit.windowSeconds());
        if (!allowed) {
            statService.incr("ratelimit:blocked");
            throw new RateLimitException("请求过于频繁，已被限流（QPS=" + rateLimit.qps() + "）");
        }
        return pjp.proceed();
    }
}
