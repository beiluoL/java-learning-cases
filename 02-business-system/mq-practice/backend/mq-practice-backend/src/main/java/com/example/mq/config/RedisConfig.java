package com.example.mq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis 配置。
 * <p>
 * 只声明一样东西：<b>Lua 脚本</b>。其余交给 Spring Boot 自动装配的 {@code StringRedisTemplate}——
 * 它内部已经用 {@link org.springframework.data.redis.serializer.StringRedisSerializer} 序列化
 * key 与 value，所以在 redis-cli 里看到的是可读文本而不是 JDK 序列化乱码。
 * （自建 {@code RedisTemplate<Object,Object>} 而不指定序列化器，是新手最常踩的坑之一。）
 * <p>
 * 脚本 Bean 在启动时加载一次，之后走 EVALSHA 复用，避免每次调用都把脚本文本传到 Redis。
 */
@Configuration
public class RedisConfig {

    @Bean
    public DefaultRedisScript<Long> seckillStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_stock.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
