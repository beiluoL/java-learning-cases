package com.example.redislab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * 全栈 Redis 实战项目启动类。
 *
 * <p>@EnableRedisHttpSession：开启「Spring Session 存 Redis」，
 * maxInactiveIntervalInSeconds=1800 表示会话 30 分钟无活动即失效。
 * 多实例部署时，任意实例写入的会话都在 Redis 中，其它实例可读取，从而实现分布式会话共享。</p>
 */
@SpringBootApplication
@MapperScan("com.example.redislab.mapper")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class RedisLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisLabApplication.class, args);
    }
}
