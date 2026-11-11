package com.example.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册管理接口拦截器。业务接口 /api/ai/chat 不受影响。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminAccessInterceptor interceptor;

    public WebConfig(AdminAccessInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/admin/**");
    }
}
