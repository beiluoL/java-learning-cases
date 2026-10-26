package com.enterprise.erpcrm.framework.security;

/**
 * 登录用户上下文持有者（ThreadLocal）。
 * <p>请求经 {@code JwtAuthInterceptor} 校验后写入，业务层随时取用；请求结束务必 {@link #clear()}。</p>
 *
 * @author erp-crm-system
 */
public final class SecurityHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private SecurityHolder() {
    }

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static boolean hasLogin() {
        return CONTEXT.get() != null;
    }

    public static Long getUserId() {
        LoginUser u = CONTEXT.get();
        return u == null ? null : u.getUserId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
