package com.learn.mybatismini.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 拦截器责任链（对应 MyBatis 的 {@code InterceptorChain}）。
 *
 * <p>{@link #pluginAll} 把同一个目标对象依次交给每个拦截器包装：
 * <pre>
 *   target -> p1.plugin(target) -> p2.plugin(...) -> p3.plugin(...)
 * </pre>
 * 最终返回一个被多层代理包裹的对象。Configuration 在创建 Executor / StatementHandler
 * 等核心对象后都会过一遍这条链。
 */
public class InterceptorChain {

    /** 按 <plugins> 配置顺序注册的所有拦截器 */
    private final List<Interceptor> interceptors = new ArrayList<>();

    public void addInterceptor(Interceptor interceptor) {
        interceptors.add(interceptor);
    }

    /**
     * 责任链套娃：返回包装后的对象（可能仍是原对象，若没有任何插件关注它）。
     */
    public Object pluginAll(Object target) {
        for (Interceptor interceptor : interceptors) {
            target = interceptor.plugin(target);
        }
        return target;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }
}
