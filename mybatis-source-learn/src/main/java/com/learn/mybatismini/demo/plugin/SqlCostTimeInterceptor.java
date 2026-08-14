package com.learn.mybatismini.demo.plugin;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.plugin.Interceptor;
import com.learn.mybatismini.plugin.Intercepts;
import com.learn.mybatismini.plugin.Invocation;
import com.learn.mybatismini.plugin.Plugin;
import com.learn.mybatismini.plugin.Signature;

import java.util.Properties;

/**
 * 慢 SQL 耗时统计插件（自定义拦截器）。
 *
 * <h3>这是 MyBatis 插件机制（Interceptor）的"Hello World"级示例</h3>
 * 真实项目里 PageHelper 分页、数据脱敏、审计日志、读写分离路由都是用同样的套路实现的。
 *
 * <h3>它做了什么？</h3>
 * 用 {@code @Intercepts} 声明要拦截 {@link Executor} 的 {@code query} 与 {@code update} 方法。
 * 每次 SQL 执行前后各取一次时间戳，算出耗时并打印；超过 {@code slowSqlMillis} 则告警。
 *
 * <h3>原理一句话</h3>
 * MyBatis 在创建 Executor 时会把它依次交给所有插件包装成 JDK 动态代理
 * （见 {@code InterceptorChain.pluginAll}）。调用 {@code executor.query(...)} 时先经过这里，
 * 这里再 {@code invocation.proceed()} 真正放行到下一层 —— 这就是 AOP 的"环绕通知"。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class SqlCostTimeInterceptor implements Interceptor {

    /** 慢 SQL 阈值（毫秒），由 <property name="slowSqlMillis"> 配置；默认 10ms */
    private long slowSqlMillis = 10;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            // 交给责任链的下一环（下一个插件，或真正的 Executor）
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            String method = invocation.getMethod().getName();
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            MiniLog.info("插件[耗时统计] " + method + " " + ms.getId() + " 耗时 " + cost + "ms");
            if (cost > slowSqlMillis) {
                MiniLog.warn("插件[慢SQL] 执行超过 " + slowSqlMillis + "ms -> " + ms.getId());
            }
        }
    }

    /** 框架在解析 <plugin> 时会把 <property> 注入进来 */
    @Override
    public void setProperties(Properties properties) {
        String millis = properties.getProperty("slowSqlMillis");
        if (millis != null && !millis.isEmpty()) {
            this.slowSqlMillis = Long.parseLong(millis);
        }
    }

    /** 默认实现就是 Plugin.wrap，这里显式写出便于对照源码 */
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(this, target);
    }
}
