package com.learn.springmini.aop;

/**
 * AOP 代理抽象 —— 对应 Spring 的 {@code AopProxy}。
 *
 * <p>它只关心一件事：<b>产出一个代理对象</b>。具体怎么造（JDK 还是 CGLIB）由实现类决定。</p>
 */
public interface AopProxy {

    /** 用上下文类加载器生成代理对象。 */
    Object getProxy();

    /** 用指定类加载器生成代理对象。 */
    Object getProxy(ClassLoader classLoader);
}
