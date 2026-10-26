package com.ecommerce.learn.security;

import com.ecommerce.learn.common.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 拦截器：校验访问令牌，写入 {@link UserContext}
 * <p>后台接口（/api/admin/**）会额外校验 ADMIN 角色。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader(AuthConstants.HEADER_NAME);
        if (!StringUtils.hasText(header) || !header.startsWith(AuthConstants.TOKEN_PREFIX)) {
            throw new UnauthorizedException("缺少或格式错误的 Authorization 头");
        }
        String token = header.substring(AuthConstants.TOKEN_PREFIX.length());
        Claims claims;
        try {
            claims = jwtTokenProvider.parse(token);
        } catch (Exception e) {
            throw new UnauthorizedException("令牌无效或已过期");
        }
        if (jwtTokenProvider.isExpired(claims)) {
            throw new UnauthorizedException("令牌已过期");
        }
        Long userId = jwtTokenProvider.getUserId(claims);
        String username = claims.get(AuthConstants.CLAIM_USERNAME, String.class);
        String role = claims.get(AuthConstants.CLAIM_ROLE, String.class);
        UserContext.set(new LoginUser(userId, username, role));

        // 后台接口强制管理员
        String uri = request.getRequestURI();
        if (uri.contains("/api/admin/") && !"ADMIN".equals(role)) {
            throw new com.ecommerce.learn.common.exception.ForbiddenException("需要管理员权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
