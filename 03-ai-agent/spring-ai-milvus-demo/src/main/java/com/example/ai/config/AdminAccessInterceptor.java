package com.example.ai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理接口权限保护（/api/admin/**）：
 * - 配置 AI_ADMIN_TOKEN 时，要求请求头 X-Admin-Token 常量时间匹配；
 * - 未配置时仅允许本机访问，供本地开发联调；
 * - 任何情况下都不打印令牌，也不允许前端决定用户身份或成本。
 */
@Component
public class AdminAccessInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Admin-Token";

    private final byte[] configuredToken;

    public AdminAccessInterceptor(@Value("${AI_ADMIN_TOKEN:}") String token) {
        this.configuredToken = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean allowed;
        if (configuredToken.length > 0) {
            String given = request.getHeader(HEADER);
            allowed = given != null && MessageDigest.isEqual(
                    given.getBytes(StandardCharsets.UTF_8), configuredToken);
        } else {
            String remote = request.getRemoteAddr();
            allowed = "127.0.0.1".equals(remote) || "::1".equals(remote)
                    || "0:0:0:0:0:0:0:1".equals(remote);
        }
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40301,\"message\":\"管理接口需要管理员令牌（仅本机可免令牌访问）\",\"data\":null,\"traceId\":\"\"}");
            return false;
        }
        return true;
    }
}
