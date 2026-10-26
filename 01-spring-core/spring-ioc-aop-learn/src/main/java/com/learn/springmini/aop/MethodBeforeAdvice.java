package com.learn.springmini.aop;

import java.lang.reflect.Method;

/**
 * 前置通知 —— 对应 Spring 的 {@code MethodBeforeAdvice}。
 *
 * <p>在目标方法<b>执行之前</b>被调用。典型用途：权限校验、参数打印、计时开始等。</p>
 */
public interface MethodBeforeAdvice extends Advice {

    /**
     * 目标方法执行前回调。
     *
     * @param method 即将被执行的目标方法
     * @param args   方法实参
     * @param target 目标对象
     */
    void before(Method method, Object[] args, Object target) throws Throwable;
}
