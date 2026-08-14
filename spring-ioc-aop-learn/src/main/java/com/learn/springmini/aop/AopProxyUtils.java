package com.learn.springmini.aop;

/**
 * AOP 代理工具 —— 对应 Spring 的 {@code AopProxyUtils}（精简版）。
 */
public final class AopProxyUtils {

    private AopProxyUtils() {
    }

    /** 返回目标对象实现的所有接口（JDK 动态代理需要基于接口创建）。 */
    public static Class<?>[] getAllInterfaces(Object target) {
        if (target == null) {
            return new Class<?>[0];
        }
        return target.getClass().getInterfaces();
    }
}
