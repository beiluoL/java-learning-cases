package com.learn.mybatismini.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 插件代理包装器（对应 MyBatis 的 {@code Plugin}）。
 *
 * <h3>它是 JDK 动态代理的 {@link InvocationHandler}</h3>
 * <pre>
 *   Plugin.wrap(interceptor, target)
 *        │ 为 target 创建动态代理，代理的 invoke 逻辑就是本类
 *        ▼
 *   proxy.query(...)  ──► Plugin.invoke
 *        │   判断当前方法是否在 @Signature 列表里
 *        ├─ 命中 ─► interceptor.intercept(new Invocation(target, method, args))
 *        └─ 未命中 ─► method.invoke(target, args)  直接放行
 * </pre>
 *
 * <p>因为 {@code Interceptor.plugin(target)} 默认就是 {@code Plugin.wrap(this, target)}，
 * 多个插件会被一层层套娃：外层插件的 invoke 调到内层代理，内层再调下一层，
 * 直到最里层才是真实 Executor。
 */
public class Plugin implements InvocationHandler {

    /** 拦截器本体（持有真正的拦截逻辑） */
    private final Interceptor target;

    /** 被代理的原始对象 */
    private final Object delegate;

    /** 拦截器关注的：类 -> 该类下要拦截的方法集合 */
    private final Map<Class<?>, Set<Method>> signatureMap;

    private Plugin(Interceptor target, Object delegate, Map<Class<?>, Set<Method>> signatureMap) {
        this.target = target;
        this.delegate = delegate;
        this.signatureMap = signatureMap;
    }

    /**
     * 为 {@code target} 创建代理。只有当 target 所属类命中签名时才代理，
     * 否则原样返回（避免无意义的代理包装）。
     */
    public static Object wrap(Interceptor target, Object delegate) {
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(target);
        Class<?> type = delegate.getClass();
        // 找出 target 实现的、且被拦截器关注的接口
        Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
        if (interfaces.length > 0) {
            return Proxy.newProxyInstance(
                    type.getClassLoader(),
                    interfaces,
                    new Plugin(target, delegate, signatureMap));
        }
        return delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Set<Method> methods = signatureMap.get(method.getDeclaringClass());
        if (methods != null && methods.contains(method)) {
            // ★ 命中签名：进入插件拦截逻辑
            return target.intercept(new Invocation(delegate, method, args));
        }
        // 未命中：直接调到被代理对象
        return method.invoke(delegate, args);
    }

    /**
     * 解析 @Intercepts 里的所有 @Signature，组织成 类->方法集合。
     */
    private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
        Intercepts intercepts = interceptor.getClass().getAnnotation(Intercepts.class);
        if (intercepts == null) {
            throw new PluginException("拦截器 " + interceptor.getClass().getName()
                    + " 必须加上 @Intercepts 注解");
        }
        Map<Class<?>, Set<Method>> map = new HashMap<>();
        for (Signature signature : intercepts.value()) {
            Set<Method> methods = map.computeIfAbsent(signature.type(), k -> new HashSet<>());
            try {
                Method method = signature.type().getMethod(signature.method(), signature.args());
                methods.add(method);
            } catch (NoSuchMethodException e) {
                throw new PluginException("找不到被拦截的方法：" + signature.type().getName()
                        + "." + signature.method(), e);
            }
        }
        return map;
    }

    /**
     * 取 target 实现的所有接口中、被拦截器关注的那些。
     */
    private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
        Set<Class<?>> interfaces = new HashSet<>();
        while (type != null) {
            for (Class<?> anInterface : type.getInterfaces()) {
                if (signatureMap.containsKey(anInterface)) {
                    interfaces.add(anInterface);
                }
            }
            type = type.getSuperclass();
        }
        return interfaces.toArray(new Class<?>[0]);
    }
}
