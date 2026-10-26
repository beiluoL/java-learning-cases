package com.learn.springmini.aop;

import java.lang.reflect.Method;

/**
 * 方法匹配器 —— 对应 Spring 的 {@code MethodMatcher}。
 *
 * <p>判断“某个方法要不要被增强”。本手写版只支持静态匹配（不依赖运行时参数），
 * 因此只看方法签名即可。支持精确方法名匹配或以 {@code *} 结尾的前缀匹配（见 {@code NameMatchMethodPointcut}）。</p>
 */
public interface MethodMatcher {

    boolean matches(Method method, Class<?> targetClass);
}
