package com.finpay.security.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 令牌提供器。用于无状态鉴权：登录签发 token，后续请求携带 token 经 {@link JwtAuthFilter} 校验。
 * 密钥同样来自配置中心，禁止硬编码。
 */
@Component
public class JwtTokenProvider {

    @Value("${finpay.security.jwt-secret:finpay-default-secret-key-please-change-32b}")
    private String secret;

    @Value("${finpay.security.jwt-expire-seconds:7200}")
    private long expireSeconds;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList());
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public boolean validate(String token) {
        try { parse(token); return true; } catch (Exception e) { return false; }
    }

    public String username(String token) {
        return parse(token).getSubject();
    }
}
