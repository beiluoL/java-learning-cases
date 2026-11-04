package com.example.redislab.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 手动构建 RedissonClient，而不是依赖 starter 自动配置，
 * 这样版本与属性来源都可控，避免不同 Boot 版本下自动配置踩坑。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setAddress(address).setPassword(password);
        } else {
            config.useSingleServer().setAddress(address);
        }
        return Redisson.create(config);
    }
}
