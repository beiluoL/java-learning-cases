package com.finpay.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加解密相关配置。密钥严禁硬编码进代码仓库，生产环境应通过 KMS / 配置中心注入。
 * 本地开发可用沙箱测试密钥，详见《支付场景详细文档》。
 */
@Data
@ConfigurationProperties(prefix = "finpay.crypto")
public class CryptoProperties {

    /** 平台 AES 主密钥（Base64，16 字节=128 位） */
    private String aesKey;

    /** 平台 RSA 私钥（PKCS8 Base64），用于「平台签名」 */
    private String rsaPrivateKey;

    /** 平台 RSA 公钥（Base64），渠道持有用于验签 */
    private String rsaPublicKey;

    /** 是否开启调试模式（打印脱敏日志）。生产必须 false */
    private boolean debug = false;
}
