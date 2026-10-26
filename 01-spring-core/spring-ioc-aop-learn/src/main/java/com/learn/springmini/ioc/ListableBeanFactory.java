package com.learn.springmini.ioc;

/**
 * 可列举的 BeanFactory —— 对应 Spring 的 {@code ListableBeanFactory}。
 *
 * <p>在基础 {@link BeanFactory} 之上增加“按类型批量查询 Bean”的能力。
 * 我们的 AOP 自动代理器（{@code DefaultAdvisorAutoProxyCreator}）正是靠这个方法，
 * 一次性把所有 {@code Advisor} 类型的 Bean 找出来，从而对匹配的 Bean 织入代理。</p>
 */
public interface ListableBeanFactory extends BeanFactory {

    /**
     * 返回容器中所有类型兼容 {@code type} 的 Bean（key 为 Bean 名称，value 为实例）。
     * 会触发这些 Bean 的实例化（因为它们是单例，取一次就会被缓存）。
     */
    <T> java.util.Map<String, T> getBeansOfType(Class<T> type) throws BeansException;
}
