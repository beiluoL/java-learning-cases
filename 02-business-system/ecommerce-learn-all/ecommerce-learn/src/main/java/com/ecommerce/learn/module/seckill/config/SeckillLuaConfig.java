package com.ecommerce.learn.module.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 秒杀 Lua 脚本 Bean（原子扣减库存 + 防重复购买）
 */
@Configuration
public class SeckillLuaConfig {

    @Bean
    public RedisScript<Long> seckillScript() {
        return RedisScript.of(new ClassPathResource("lua/seckill.lua"), Long.class);
    }
}
