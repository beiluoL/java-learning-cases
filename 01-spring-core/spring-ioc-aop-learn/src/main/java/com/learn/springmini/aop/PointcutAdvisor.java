package com.learn.springmini.aop;

/**
 * 带切点的通知器 —— 对应 Spring 的 {@code PointcutAdvisor}。
 *
 * <p>相比单纯的 {@link Advisor}，它明确持有 {@link Pointcut}，能精确控制增强范围。</p>
 */
public interface PointcutAdvisor extends Advisor {

    Pointcut getPointcut();
}
