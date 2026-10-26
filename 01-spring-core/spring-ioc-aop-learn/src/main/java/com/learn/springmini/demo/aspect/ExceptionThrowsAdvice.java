package com.learn.springmini.demo.aspect;

import com.learn.springmini.aop.ThrowsAdvice;

import java.lang.reflect.Method;

/**
 * 自定义【异常通知】—— 当目标方法抛出异常时被调用，用于告警、记录或触发补偿。
 *
 * <p>实现 {@link ThrowsAdvice}。本例只打印异常信息，并原样继续抛出（不吞异常）。</p>
 */
public class ExceptionThrowsAdvice implements ThrowsAdvice {

    @Override
    public void afterThrowing(Method method, Object[] args, Object target, Throwable ex) {
        System.out.println("  [异常通知] 方法 " + method.getName()
                + " 抛出异常: " + ex);
    }
}
