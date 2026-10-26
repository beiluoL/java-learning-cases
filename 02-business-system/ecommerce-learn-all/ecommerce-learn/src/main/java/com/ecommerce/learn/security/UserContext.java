package com.ecommerce.learn.security;

import com.ecommerce.learn.common.exception.ForbiddenException;
import com.ecommerce.learn.common.exception.UnauthorizedException;

/**
 * 登录态容器：基于 ThreadLocal，在 JWT 拦截器中写入、请求结束后清理
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new UnauthorizedException("用户未登录");
        }
        return user;
    }

    public static Long getUserId() {
        return get().getUserId();
    }

    public static String getRole() {
        return get().getRole();
    }

    /** 要求管理员角色，否则抛 403 */
    public static void requireAdmin() {
        if (!"ADMIN".equals(get().getRole())) {
            throw new ForbiddenException("需要管理员权限");
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
