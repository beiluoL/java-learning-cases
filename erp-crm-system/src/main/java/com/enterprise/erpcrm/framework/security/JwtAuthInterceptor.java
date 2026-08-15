package com.enterprise.erpcrm.framework.security;

import com.enterprise.erpcrm.common.result.RCode;
import com.enterprise.erpcrm.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器：拦截需鉴权的请求，校验 Token 合法性并装配 {@link LoginUser} 到 {@link SecurityHolder}。
 * <p>白名单（{@code erp.security.ignore-urls}）放行；校验失败直接写入 401 JSON，不进入 Controller。</p>
 *
 * @author erp-crm-system
 */
@Slf4j
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtProvider jwtProvider;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthInterceptor(JwtProvider jwtProvider, SecurityProperties securityProperties) {
        this.jwtProvider = jwtProvider;
        this.securityProperties = securityProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String uri = request.getRequestURI();
        if (isIgnore(uri)) {
            return true;
        }
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "缺失令牌");
            return false;
        }
        try {
            Claims claims = jwtProvider.parseToken(token);
            LoginUser user = buildLoginUser(claims);
            SecurityHolder.set(user);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token 校验失败 uri={} msg={}", uri, e.getMessage());
            writeUnauthorized(response, "令牌无效或已过期");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 防止 ThreadLocal 在线程池场景下泄漏
        SecurityHolder.clear();
    }

    private LoginUser buildLoginUser(Claims claims) {
        Long userId = claims.get("userId", Long.class);
        Long deptId = claims.get("deptId", Long.class);
        Integer dataScope = claims.get("dataScope", Integer.class);
        String username = claims.getSubject();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        @SuppressWarnings("unchecked")
        List<String> perms = claims.get("perms", List.class);
        boolean superAdmin = roles != null && roles.contains(securityProperties.getSuperAdminRole());
        return LoginUser.builder()
                .userId(userId)
                .username(username)
                .deptId(deptId)
                .dataScope(dataScope)
                .roles(roles == null ? List.of() : roles)
                .perms(perms == null ? List.of() : perms)
                .superAdmin(superAdmin)
                .build();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearer)) {
            return null;
        }
        String prefix = "Bearer ";
        if (bearer.startsWith(prefix)) {
            return bearer.substring(prefix.length()).trim();
        }
        return bearer.trim();
    }

    private boolean isIgnore(String uri) {
        for (String pattern : securityProperties.getIgnoreUrls()) {
            if (pathMatcher.match(pattern, uri)) {
                return true;
            }
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK); // 业务统一 200 + code=401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Result<Void> result = Result.error(RCode.UNAUTHORIZED, msg);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
