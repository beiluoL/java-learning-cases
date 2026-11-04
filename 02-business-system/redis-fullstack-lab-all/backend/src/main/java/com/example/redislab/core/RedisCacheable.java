package com.example.redislab.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义缓存注解（教学用，对比 Spring 自带 @Cacheable）。
 * 标注在方法上：返回值会被缓存到指定 key，TTL 由 ttl 指定（秒）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisCacheable {
    String key();
    long ttl() default 300;
}
