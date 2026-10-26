package com.example.videostream.service.cdn;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.properties.CdnProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * 播放地址防盗链签名（HMAC-SHA256）。
 * 生成格式：{baseUrl}?exp={unix秒}&token={签名}
 * 边缘节点按相同算法校验 exp 未过期且 token 一致，拒绝非法请求。
 */
@Slf4j
@Component
public class UrlSignService {

    private final CdnProperties cdnProperties;

    public UrlSignService(CdnProperties cdnProperties) {
        this.cdnProperties = cdnProperties;
    }

    /**
     * 对给定基础 URL 进行签名。
     *
     * @param baseUrl 不含查询参数的基础 URL（如 https://cdn.example.com/bucket/key.m3u8）
     * @param ttlSeconds 有效期（秒）
     * @return 带 exp & token 的签名 URL
     */
    public String sign(String baseUrl, long ttlSeconds) {
        long expire = Instant.now().getEpochSecond() + ttlSeconds;
        String raw = baseUrl + expire;
        String token = hmacSha256(raw, cdnProperties.getSignSecret());
        return baseUrl + "?exp=" + expire + "&token=" + token;
    }

    /** 校验签名是否有效（边缘侧/服务端校验用）。 */
    public boolean verify(String baseUrl, long expire, String token) {
        if (Instant.now().getEpochSecond() > expire) {
            return false;
        }
        String expected = hmacSha256(baseUrl + expire, cdnProperties.getSignSecret());
        return expected.equals(token);
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("hmacSha256 failed", e);
            throw new BizException(ErrorCode.URL_SIGN_FAILED);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
