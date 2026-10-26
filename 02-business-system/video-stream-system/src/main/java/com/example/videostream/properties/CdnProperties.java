package com.example.videostream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CDN 多节点调度与播放鉴权配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cdn")
public class CdnProperties {
    /** 区域 -> CDN 域名映射（AP/EU/US/DEFAULT） */
    private Map<String, String> regionDomains = new LinkedHashMap<>();
    /** 播放鉴权 HMAC 签名密钥 */
    private String signSecret;
    /** 默认播放地址有效期（秒） */
    private int defaultTtlSeconds = 3600;
}
