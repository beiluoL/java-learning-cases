package com.enterprise.erpcrm.framework.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 提供方：基于 jjwt 0.12.x 实现 Token 签发与解析。
 * <p>Payload  Claims：sub=username，userId，roles(List)，perms(List)。</p>
 *
 * @author erp-crm-system
 */
@Component
public class JwtProvider {

    private final JwtProperties props;

    public JwtProvider(JwtProperties props) {
        this.props = props;
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 Token */
    public String generateToken(String username, Long userId, Long deptId, Integer dataScope,
                                List<String> roles, List<String> perms) {
        long now = System.currentTimeMillis();
        long exp = now + props.getExpireMinutes() * 60_000L;
        Map<String, Object> claims = new HashMap<>(6);
        claims.put("userId", userId);
        claims.put("deptId", deptId == null ? 0L : deptId);
        claims.put("dataScope", dataScope == null ? 5 : dataScope);
        claims.put("roles", roles);
        claims.put("perms", perms);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key())
                .compact();
    }

    /** 解析 Token，失败抛 JwtException */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /** 是否即将过期（小于 10 分钟），可用于前端续期提示 */
    public boolean isNearExpiry(String token) {
        try {
            Date exp = parseToken(token).getExpiration();
            return exp.getTime() - System.currentTimeMillis() < 10 * 60_000L;
        } catch (JwtException e) {
            return true;
        }
    }
}
