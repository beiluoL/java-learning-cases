package com.ecommerce.learn.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与校验访问/刷新令牌
 * <p>算法 HS256，密钥从配置注入（生产应使用 32+ 字节随机密钥并妥善保管）。</p>
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-token-ttl:7200}")
    private long accessTtl;

    @Value("${jwt.refresh-token-ttl:604800}")
    private long refreshTtl;

    private SecretKey key;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String username, String role) {
        return buildToken(String.valueOf(userId), username, role, accessTtl);
    }

    public String createRefreshToken(Long userId, String username, String role) {
        return buildToken(String.valueOf(userId), username, role, refreshTtl);
    }

    private String buildToken(String subject, String username, String role, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim(AuthConstants.CLAIM_USERNAME, username)
                .claim(AuthConstants.CLAIM_ROLE, role)
                .issuer(issuer)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000L))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析并校验令牌，失败抛 IllegalArgumentException */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
