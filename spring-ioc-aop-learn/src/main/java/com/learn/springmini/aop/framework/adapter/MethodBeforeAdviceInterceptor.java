package com.learn.springmini.aop.framework.adapter;

import com.learn.springmini.aop.MethodBeforeAdvice;
import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;

/**
 * 前置通知适配器 —— 把 {@link MethodBeforeAdvice} 包装成统一的 {@link MethodInterceptor}。
 *
 * <p>执行顺序：<b>先执行前置逻辑，再 proceed() 继续链</b>。因此前置通知一定在目标方法之前运行。</p>
 */
public class MethodBeforeAdviceInterceptor implements MethodInterceptor {

    private final MethodBeforeAdvice advice;

    public MethodBeforeAdviceInterceptor(MethodBeforeAdvice advice) {
        this.advice = advice;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 目标方法执行前
        advice.before(invocation.getMethod(), invocation.getArguments(), invocation.getThis());
        // 继续链（最终到达目标方法）
        return invocation.proceed();
    }
}
