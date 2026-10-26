package com.learn.springmini.aop.framework.adapter;

import com.learn.springmini.aop.AfterReturningAdvice;
import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;

/**
 * 后置返回通知适配器 —— 把 {@link AfterReturningAdvice} 包装成 {@link MethodInterceptor}。
 *
 * <p>执行顺序：<b>先 proceed() 拿到目标方法返回值，再执行后置逻辑</b>。
 * 因此它能看到返回值，但若目标抛异常则不会执行到这里。</p>
 */
public class AfterReturningAdviceInterceptor implements MethodInterceptor {

    private final AfterReturningAdvice advice;

    public AfterReturningAdviceInterceptor(AfterReturningAdvice advice) {
        this.advice = advice;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 先执行目标方法（及链上更内层的拦截器）
        Object retVal = invocation.proceed();
        // 目标正常返回后，再执行后置逻辑（可以看到返回值）
        advice.afterReturning(retVal, invocation.getMethod(), invocation.getArguments(), invocation.getThis());
        return retVal;
    }
}
