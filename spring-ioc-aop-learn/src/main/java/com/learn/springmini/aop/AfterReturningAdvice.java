package com.learn.springmini.aop;

import java.lang.reflect.Method;

/**
 * 后置返回通知 —— 对应 Spring 的 {@code AfterReturningAdvice}。
 *
 * <p>在目标方法<b>正常返回之后</b>被调用（若方法抛异常则不会执行到这里）。
 * 可以拿到目标方法的返回值，典型用途：结果日志、审计记录。</p>
 */
public interface AfterReturningAdvice extends Advice {

    /**
     * 目标方法正常返回后回调。
     *
     * @param returnValue 目标方法的返回值（可能为 null）
     * @param method      被执行的方法
     * @param args        方法实参
     * @param target      目标对象
     */
    void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable;
}
