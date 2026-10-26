package com.example.videostream.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置（S3 兼容接口）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "object-storage")
public class ObjectStorageProperties {
    /** 标准端点 */
    private String endpoint;
    /** 区域 */
    private String region;
    /** 桶名 */
    private String bucket;
    /** AK */
    private String accessKey;
    /** SK */
    private String secretKey;
    /** 全球上传加速端点（可选） */
    private String accelerateEndpoint;
}
