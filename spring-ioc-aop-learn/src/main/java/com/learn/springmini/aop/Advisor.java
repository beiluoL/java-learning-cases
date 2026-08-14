package com.learn.springmini.aop;

/**
 * 通知器（Advisor）—— 对应 Spring 的 {@code Advisor}。
 *
 * <p>Advisor 把“<b>在什么地方织入</b>（Pointcut）”和“<b>织入什么逻辑</b>（Advice）”绑定在一起，
 * 是 AOP 里最小的“可配置增强单元”。一个 Advisor = 一个切点 + 一个通知。</p>
 */
public interface Advisor {

    /** 该 Advisor 携带的通知（横切逻辑）。 */
    Advice getAdvice();
}
