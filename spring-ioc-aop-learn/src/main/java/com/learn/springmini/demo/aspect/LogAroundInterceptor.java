package com.learn.springmini.demo.aspect;

import com.learn.springmini.aop.MethodInterceptor;
import com.learn.springmini.aop.MethodInvocation;

/**
 * 自定义【环绕通知】—— 最强大的通知，可在目标方法前后都插入逻辑，并掌控是否继续执行。
 *
 * <p>实现 {@link MethodInterceptor}（等价于 Spring 的 {@code @Around}）。
 * 这里用它在方法前后打印日志并统计耗时，是“方法级监控/性能统计”的典型写法。</p>
 */
public class LogAroundInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String name = invocation.getMethod().getName();
        long start = System.currentTimeMillis();
        System.out.println("  [环绕通知] >>> 进入方法 " + name);
        try {
            // 继续执行链（最终到达目标方法）
            Object result = invocation.proceed();
            long cost = System.currentTimeMillis() - start;
            System.out.println("  [环绕通知] <<< 离开方法 " + name + "，耗时=" + cost + "ms");
            return result;
        } catch (Throwable t) {
            // 异常情况下也记一笔（注意：这里选择不吞异常，继续向上抛）
            System.out.println("  [环绕通知] <<< 方法 " + name + " 抛异常: " + t);
            throw t;
        }
    }
}
