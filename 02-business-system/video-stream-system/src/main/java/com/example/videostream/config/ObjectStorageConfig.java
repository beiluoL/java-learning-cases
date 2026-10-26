package com.example.videostream.config;

import com.example.videostream.properties.ObjectStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储客户端配置（S3 兼容）。
 * 通过 path-style / virtual-hosted 取决于厂商；本例以标准端点构建。
 */
@Slf4j
@Configuration
public class ObjectStorageConfig {

    private final ObjectStorageProperties props;

    public ObjectStorageConfig(ObjectStorageProperties props) {
        this.props = props;
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(props.getRegion()))
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        // 预签名器必须使用与 S3Client 一致的端点和区域
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));
        // 预签名 URL 不走 endpointOverride（默认用标准域名），如需加速可在此扩展
        return builder.build();
    }
}
