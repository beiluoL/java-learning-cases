package com.learn.springmini.aop;

import java.lang.reflect.Method;

/**
 * 异常通知 —— 对应 Spring 的 {@code ThrowsAdvice}。
 *
 * <p>当目标方法<b>抛出异常时</b>被调用。典型用途：异常告警、事务回滚触发、兜底处理。
 * 注意：它只是“被告知”发生了异常，默认不会吞掉异常（我们实现里会原样继续抛出）。</p>
 */
public interface ThrowsAdvice extends Advice {

    /**
     * 目标方法抛出异常后回调。
     *
     * @param method 抛出异常的方法
     * @param args   方法实参
     * @param target 目标对象
     * @param ex     被抛出的异常
     */
    void afterThrowing(Method method, Object[] args, Object target, Throwable ex) throws Throwable;
}
