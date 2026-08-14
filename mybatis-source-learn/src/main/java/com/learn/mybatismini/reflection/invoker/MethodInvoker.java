package com.learn.mybatismini.reflection.invoker;

import java.lang.reflect.Method;

/**
 * 基于 getter/setter <b>方法</b>的访问器。
 *
 * <p>这是最常用的实现：只要实体类遵循 JavaBean 规范写了 getXxx/setXxx，
 * Reflector 就会为该属性注册一个 MethodInvoker。
 *
 * <h3>为什么优先用 setter 而不是直接写字段？</h3>
 * 因为 setter 里可能有业务逻辑（校验、格式化、脏标记）。走 setter 才符合封装语义，
 * 也和 Spring 的属性注入取向一致。只有在没有 setter 时才退化到字段反射。
 *
 * @author mybatis-source-learn
 */
public class MethodInvoker implements Invoker {

    private final Method method;

    /** getter -> 返回值类型；setter -> 第一个入参类型 */
    private final Class<?> type;

    public MethodInvoker(Method method) {
        this.method = method;
        if (method.getParameterTypes().length == 1) {
            // setter
            this.type = method.getParameterTypes()[0];
        } else {
            // getter
            this.type = method.getReturnType();
        }
        // 关闭 JDK 的访问检查，提升反射调用性能（也能访问非 public 方法）
        this.method.setAccessible(true);
    }

    @Override
    public Object invoke(Object target, Object[] args) throws Exception {
        return method.invoke(target, args);
    }

    @Override
    public Class<?> getType() {
        return type;
    }
}
