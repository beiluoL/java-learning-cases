package com.learn.springmini.aop.framework.adapter;

import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;
import com.learn.springmini.aop.ThrowsAdvice;

/**
 * 异常通知适配器 —— 把 {@link ThrowsAdvice} 包装成 {@link MethodInterceptor}。
 *
 * <p>执行顺序：<b>用 try/catch 包住 proceed()</b>。目标方法抛异常时进入 catch，
 * 调用 afterThrowing；本实现选择把异常原样继续抛出（不吞异常）。
 * 由于它通常排在最内层（最贴近目标），所以能捕获到目标方法的异常。</p>
 */
public class ThrowsAdviceInterceptor implements MethodInterceptor {

    private final ThrowsAdvice advice;

    public ThrowsAdviceInterceptor(ThrowsAdvice advice) {
        this.advice = advice;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } catch (Throwable ex) {
            advice.afterThrowing(invocation.getMethod(), invocation.getArguments(), invocation.getThis(), ex);
            // 默认不吞掉异常，继续向上抛
            throw ex;
        }
    }
}
