package com.learn.springmini.aop;

/**
 * 类过滤器 —— 对应 Spring 的 {@code ClassFilter}。
 *
 * <p>切点（Pointcut）由“类过滤 + 方法匹配”两部分组成。类过滤器先判断“这个类要不要管”，
 * 只有类通过、方法也匹配，通知才会织入。{@link #TRUE} 表示“对所有类都放行”。</p>
 */
public interface ClassFilter {

    boolean matches(Class<?> clazz);

    /** 永远匹配（不做类级别限制）。 */
    ClassFilter TRUE = clazz -> true;
}
