package com.learn.springmini.aop;

import java.lang.reflect.Method;

/**
 * 方法调用上下文 —— 对应 Spring 的 {@code MethodInvocation}（继承自 {@code Joinpoint}）。
 *
 * <p>它是拦截器链“接力棒”：每个拦截器拿到它，调用 {@link #proceed()} 就把控制权交给链上的下一个节点，
 * 直到链尾才真正执行目标方法。没有它，环绕/前置/后置就无法串联成“链”。</p>
 */
public interface MethodInvocation {

    /** 继续执行链上的下一个拦截器；若已到链尾，则执行目标方法本身。 */
    Object proceed() throws Throwable;

    /** 当前被拦截的方法。 */
    Method getMethod();

    /** 方法实参（可读取，理论上也可改写后再 proceed）。 */
    Object[] getArguments();

    /** 目标对象（被代理的原始对象）。 */
    Object getThis();
}
