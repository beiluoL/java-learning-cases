package com.learn.springmini.aop.framework;

import com.learn.springmini.aop.AopProxy;
import com.learn.springmini.aop.AdvisedSupport;
import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * 基于 CGLIB 的实现 —— 对应 Spring 的 {@code CglibAopProxy}。
 *
 * <h3>CGLIB 动态代理机制</h3>
 * <ul>
 *   <li>核心 API：{@code net.sf.cglib.proxy.Enhancer} + {@code MethodInterceptor}。</li>
 *   <li><b>通过“继承”实现</b>：在运行时用 ASM 字节码技术动态生成目标类的一个<b>子类</b>，
 *       重写父类（目标）方法，把调用转发到 {@link DynamicAdvisedInterceptor#intercept}。</li>
 *   <li>优点：<b>不要求目标实现接口</b>，甚至能代理普通类；缺点：目标类不能是 final、
 *       被代理的方法不能是 final（final 方法无法被重写）。</li>
 * </ul>
 *
 * <p>Spring 在“目标没有接口”时默认走 CGLIB；本项目也采用同样策略（见 {@code ProxyFactory}）。</p>
 */
public class CglibAopProxy implements AopProxy {

    private final AdvisedSupport advised;

    public CglibAopProxy(AdvisedSupport advised) {
        this.advised = advised;
    }

    @Override
    public Object getProxy() {
        return getProxy(Thread.currentThread().getContextClassLoader());
    }

    @Override
    public Object getProxy(ClassLoader classLoader) {
        Enhancer enhancer = new Enhancer();
        enhancer.setClassLoader(classLoader);
        // 关键：以“目标类”为父类，生成它的子类（代理类）
        enhancer.setSuperclass(advised.getTargetClass());
        // 把拦截逻辑挂到生成的子类上
        enhancer.setCallback(new DynamicAdvisedInterceptor(advised));
        // 创建代理实例（会调用目标类的无参构造）
        return enhancer.create();
    }

    /**
     * CGLIB 的回调（注意：这是 cglib 自己的 MethodInterceptor，不是我们 AOP 包里的那个）。
     * 每当代理子类的方法被调用，就会进入这里的 intercept。
     */
    private static class DynamicAdvisedInterceptor implements net.sf.cglib.proxy.MethodInterceptor {

        private final AdvisedSupport advised;

        DynamicAdvisedInterceptor(AdvisedSupport advised) {
            this.advised = advised;
        }

        @Override
        public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            // 动态构建拦截器链（与 JDK 代理完全一致的逻辑）
            java.util.List<MethodInterceptor> chain = advised.getInterceptors(method);
            if (chain.isEmpty()) {
                // 无通知命中，直接调用目标对象对应方法（用 MethodProxy 更快，也可用 method.invoke）
                return methodProxy.invoke(advised.getTarget(), args);
            }
            MethodInvocation invocation =
                    new ReflectiveMethodInvocation(advised.getTarget(), method, args, chain);
            return invocation.proceed();
        }
    }
}
