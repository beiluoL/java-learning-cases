package com.example.redislab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：前端（http://localhost:5173）访问后端（http://localhost:8080）属于跨域。
 * 关键点：allowCredentials(true) 时必须配合 allowedOriginPatterns（不能写 "*" 字面量），
 * 否则浏览器会因凭据模式拒绝。这正是分布式会话场景需要携带 Cookie 的前提。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
