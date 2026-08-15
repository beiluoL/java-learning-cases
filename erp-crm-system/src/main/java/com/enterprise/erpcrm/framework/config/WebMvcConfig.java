package com.enterprise.erpcrm.framework.config;

import com.enterprise.erpcrm.framework.security.JwtAuthInterceptor;
import com.enterprise.erpcrm.framework.security.JwtProvider;
import com.enterprise.erpcrm.framework.security.SecurityProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 认证拦截器、配置跨域（CORS）。
 * <p>拦截器排除白名单之外的所有 /api/** 请求；CORS 允许前端开发服务器跨域。</p>
 *
 * @author erp-crm-system
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtProvider jwtProvider;
    private final SecurityProperties securityProperties;

    /** 允许跨域的前端源（开发态 5173 / 构建态同源） */
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:5173", "http://127.0.0.1:5173",
            "http://localhost:8080", "http://127.0.0.1:8080");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtAuthInterceptor(jwtProvider, securityProperties))
                .addPathPatterns("/api/**")
                .excludePathPatterns(securityProperties.getIgnoreUrls());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(ALLOWED_ORIGINS.toArray(new String[0]))
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
