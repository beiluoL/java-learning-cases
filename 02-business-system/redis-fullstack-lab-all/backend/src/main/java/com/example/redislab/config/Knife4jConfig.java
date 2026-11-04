package com.example.redislab.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j（基于 springdoc-openapi3）的 OpenAPI 元信息。
 * 启动后访问 http://localhost:8080/doc.html 查看中文接口文档。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI redisLabOpenAPI() {
        return new OpenAPI().info(
                new Info()
                        .title("Redis 全栈实战 API")
                        .description("Spring Boot + Vue3 + Redis 十大场景接口文档")
                        .version("1.0.0")
        );
    }
}
