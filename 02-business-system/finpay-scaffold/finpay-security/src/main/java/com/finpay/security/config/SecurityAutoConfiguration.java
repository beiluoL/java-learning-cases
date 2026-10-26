package com.finpay.security.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全模块自动配置。启用 {@link CryptoProperties} 绑定，供 KeyManager 等组件使用。
 */
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class SecurityAutoConfiguration {
}
