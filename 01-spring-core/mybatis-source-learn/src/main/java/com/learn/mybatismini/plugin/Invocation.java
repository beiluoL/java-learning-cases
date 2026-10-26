package com.learn.mybatismini.plugin;

import java.lang.reflect.Method;

/**
 * 一次方法调用上下文（对应 MyBatis 的 {@code Invocation}）。
 *
 * <p>插件在 {@link Interceptor#intercept} 里拿到它，可以读目标对象、方法、参数，
 * 并调用 {@link #proceed()} 把调用继续向下传递（相当于调父类方法）。
 */
public class Invocation {

    /** 被代理的真实目标对象（如 SimpleExecutor 实例） */
    private final Object target;

    /** 被拦截的方法 */
    private final Method method;

    /** 方法实参 */
    private final Object[] args;

    public Invocation(Object target, Method method, Object[] args) {
        this.target = target;
        this.method = method;
        this.args = args;
    }

    public Object getTarget() {
        return target;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    /**
     * 真正执行被拦截的方法（放行）。
     */
    public Object proceed() throws Throwable {
        return method.invoke(target, args);
    }
}
