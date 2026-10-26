package com.learn.springmini.aop;

/**
 * 环绕通知（方法拦截器）—— 对应 Spring AOP 的 {@code MethodInterceptor}（也等价于 {@code @Around}）。
 *
 * <p>这是<b>最强大</b>的一种通知：它包裹了整个目标方法的调用，可在前后都插入逻辑，
 * 能决定是否继续执行、修改参数、修改返回值、捕获异常。本质上它就是 AOP 拦截器链里的一个节点。</p>
 *
 * <p>所有其它类型的通知（前置/后置/异常）最终在底层都会被“适配”成这种拦截器，串成一条链。</p>
 */
public interface MethodInterceptor extends Advice {

    /**
     * 拦截目标方法的执行。
     *
     * @param invocation 方法调用上下文（含目标方法、参数、以及继续向下执行的 {@link MethodInvocation#proceed()}）
     * @return 目标方法的返回值（可被你改写）
     */
    Object invoke(MethodInvocation invocation) throws Throwable;
}
