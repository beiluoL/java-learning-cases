package com.example.redislab.core.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解：标注在 Controller 方法上即可启用滑动窗口限流。
 * 由 RateLimitAspect 拦截处理，底层走 ZSET 滑动窗口（Lua 原子操作）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** 限流维度 key，为空则自动用 类名:方法名 */
    String key() default "";

    /** 窗口内允许的最大请求数（QPS 语义） */
    int qps() default 5;

    /** 滑动窗口大小（秒） */
    int windowSeconds() default 1;
}
