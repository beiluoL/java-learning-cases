package com.learn.springmini.aop;

/**
 * 切点 —— 对应 Spring 的 {@code Pointcut}。
 *
 * <p>它回答一个核心问题：<b>“哪些类的哪些方法需要被增强？”</b>
 * 由 {@link ClassFilter}（类级）和 {@link MethodMatcher}（方法级）组合而成。
 * 这正是 AOP 中“切入点表达式（如 execution/annotation）”的抽象本质。</p>
 */
public interface Pointcut {

    ClassFilter getClassFilter();

    MethodMatcher getMethodMatcher();
}
