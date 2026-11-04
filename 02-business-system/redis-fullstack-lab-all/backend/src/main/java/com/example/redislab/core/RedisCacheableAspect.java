package com.example.redislab.core;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 自定义缓存注解的切面实现：在方法执行前查缓存，命中直接返回；
 * 未命中执行原方法并把返回值写入缓存。
 * 这是对 Spring @Cacheable 底层原理的「手写复刻」，便于理解缓存切面机制。
 */
@Aspect
@Component
public class RedisCacheableAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheableAspect(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(rc)")
    public Object around(ProceedingJoinPoint pjp, RedisCacheable rc) throws Throwable {
        String key = rc.key();
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        Object result = pjp.proceed();
        redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(rc.ttl()));
        return result;
    }
}
