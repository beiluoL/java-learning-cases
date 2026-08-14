package com.learn.mybatismini.plugin;

import java.util.Properties;

/**
 * 拦截器（插件）接口（对应 MyBatis 的 {@code Interceptor}）。
 *
 * <h3>MyBatis 最强扩展点：拦截四大对象的方法</h3>
 * <pre>
 *   Executor          执行器：query / update（分页、耗时统计）
 *   ParameterHandler  参数处理器：setParameters（加解密参数）
 *   ResultSetHandler  结果处理器：handleResultSets（字段脱敏）
 *   StatementHandler  语句处理器：prepare（改写SQL）
 * </pre>
 *
 * <p>拦截原理是<b>JDK 动态代理 + 责任链</b>：{@code Plugin.wrap} 把目标对象包成代理，
 * 代理的 {@code invoke} 里判断"当前方法是否是我关注的签名"，是则走
 * {@link #intercept}，否则直接放行。多个插件层层包裹，调用链形成洋葱模型。
 */
public interface Interceptor {

    /**
     * 拦截逻辑：在这里插入自定义行为，最后调用 {@link Invocation#proceed()} 放行。
     */
    Object intercept(Invocation invocation) throws Throwable;

    /**
     * 把目标对象包成代理（默认实现已经够用，一般不需要重写）。
     */
    default Object plugin(Object target) {
        return Plugin.wrap(this, target);
    }

    /**
     * 接收插件 <property> 配置。
     */
    default void setProperties(Properties properties) {
        // 默认空
    }
}
