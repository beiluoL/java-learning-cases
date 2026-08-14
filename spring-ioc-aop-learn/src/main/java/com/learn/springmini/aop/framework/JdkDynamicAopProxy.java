package com.learn.springmini.aop.framework;

import com.learn.springmini.aop.AopConfigException;
import com.learn.springmini.aop.AopProxy;
import com.learn.springmini.aop.AopProxyUtils;
import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;
import com.learn.springmini.aop.AdvisedSupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 基于 JDK 动态代理的实现 —— 对应 Spring 的 {@code JdkDynamicAopProxy}。</p>
 *
 * <h3>JDK 动态代理机制（面试高频）</h3>
 * <ul>
 *   <li>核心 API：{@link java.lang.reflect.Proxy#newProxyInstance} + {@link InvocationHandler}。</li>
 *   <li><b>只能代理“接口”</b>：生成的代理类会实现目标对象的所有接口，方法调用都转发到 {@link #invoke}。</li>
 *   <li>优点：JDK 原生、无需第三方依赖；缺点：目标必须实现接口。</li>
 * </ul>
 *
 * 在 {@link #invoke} 里我们动态构建拦截器链并驱动它，从而把各类通知织入目标方法。
 */
public class JdkDynamicAopProxy implements AopProxy, InvocationHandler {

    private final AdvisedSupport advised;

    public JdkDynamicAopProxy(AdvisedSupport advised) {
        this.advised = advised;
    }

    @Override
    public Object getProxy() {
        return getProxy(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Object getProxy(ClassLoader classLoader) {
        Class<?>[] interfaces = AopProxyUtils.getAllInterfaces(advised.getTarget());
        if (interfaces.length == 0) {
            throw new AopConfigException("JDK 动态代理要求目标对象至少实现一个接口");
        }
        // 生成的代理对象会实现这些接口；方法调用统一转发到 this.invoke
        return Proxy.newProxyInstance(classLoader, interfaces, this);
    }

    /**
     * 代理对象上任意接口方法的调用都会先到这里。
     * 我们据此构建拦截器链并驱动执行（无通知匹配时直接调用目标方法）。
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        List<MethodInterceptor> chain = advised.getInterceptors(method);
        if (chain.isEmpty()) {
            // 没有通知命中，直接走目标方法
            return method.invoke(advised.getTarget(), args);
        }
        MethodInvocation invocation =
                new ReflectiveMethodInvocation(advised.getTarget(), method, args, chain);
        return invocation.proceed();
    }
}
