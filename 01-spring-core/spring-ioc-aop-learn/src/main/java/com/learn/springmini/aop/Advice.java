package com.learn.springmini.aop;

/**
 * 通知（Advice）标记接口 —— 对应 Spring AOP 的 {@code Advice}。
 *
 * <p>AOP 把“横切逻辑”（日志、事务、鉴权……）抽象为 Advice。
 * 本接口本身不含方法，只是给各类通知一个共同父类型，方便容器统一处理。</p>
 */
public interface Advice {
}
