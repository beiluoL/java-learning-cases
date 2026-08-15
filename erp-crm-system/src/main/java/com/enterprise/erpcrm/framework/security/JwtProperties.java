package com.enterprise.erpcrm.framework.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置绑定（application.yml 的 {@code erp.jwt.*}）。
 * <p>secret 支持环境变量 {@code ERP_JWT_SECRET} 覆盖，生产环境务必设置。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.jwt")
public class JwtProperties {

    /** 签名密钥（Base64 字符串，jjwt 自动解码） */
    private String secret;
    /** 有效期（分钟） */
    private long expireMinutes = 720;
    /** HTTP 头名称 */
    private String header = "Authorization";
    /** Token 前缀，如 "Bearer " */
    private String tokenPrefix = "Bearer ";
}
