package com.learn.springmini.aop.framework;

import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 反射式方法调用 —— 对应 Spring 的 {@code ReflectiveMethodInvocation}。
 *
 * <p>它既是 {@link MethodInvocation}（给拦截器用的“接力棒”），又自己维护“链指针”。
 * 每次调用 {@link #proceed()}：</p>
 * <ul>
 *   <li>若还有下一个拦截器 → 取出并执行它（拦截器内部会再调回 proceed，形成递归/嵌套）；</li>
 *   <li>若已是链尾 → 直接通过反射调用目标对象的真实方法。</li>
 * </ul>
 * 这样，多个通知就像洋葱一样一层层包裹住目标方法。
 */
public class ReflectiveMethodInvocation implements MethodInvocation {

    private final Object target;
    private final Method method;
    private final Object[] arguments;
    private final List<MethodInterceptor> interceptors;

    /** 当前执行到链上的第几个拦截器（-1 表示还没开始）。 */
    private int currentIndex = -1;

    public ReflectiveMethodInvocation(Object target, Method method, Object[] arguments,
                                      List<MethodInterceptor> interceptors) {
        this.target = target;
        this.method = method;
        this.arguments = arguments;
        this.interceptors = interceptors;
    }

    @Override
    public Object proceed() throws Throwable {
        // 到达链尾 → 调用目标对象真实方法
        if (currentIndex == interceptors.size() - 1) {
            try {
                return method.invoke(target, arguments);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // 关键点：method.invoke 会把目标方法抛出的异常包成 InvocationTargetException，
                // 这里“拆包”还原成真实异常再向上抛，否则上层（通知/调用方）看到的是包装异常。
                // 这也是 Spring 的 ReflectiveMethodInvocation 所做的处理。
                throw e.getCause();
            }
        }
        // 还有下一个拦截器 → 取出并执行（它的 invoke 里会再次调用 proceed）
        MethodInterceptor interceptor = interceptors.get(++currentIndex);
        return interceptor.invoke(this);
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public Object[] getArguments() {
        return arguments;
    }

    @Override
    public Object getThis() {
        return target;
    }
}
