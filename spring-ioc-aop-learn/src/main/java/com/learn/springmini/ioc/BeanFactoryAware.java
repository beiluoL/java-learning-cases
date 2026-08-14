package com.learn.springmini.ioc;

/**
 * 感知容器接口 —— 对应 Spring 的 {@code BeanFactoryAware}。
 *
 * <p>如果某个 Bean 希望“拿到容器本身”，就实现这个接口。
 * 容器在创建该 Bean 并完成属性注入后，会把 {@link BeanFactory} 自己通过
 * {@link #setBeanFactory} 注入进来。
 *
 * 我们的 AOP 自动代理器 {@code DefaultAdvisorAutoProxyCreator} 正是借此拿到容器，
 * 进而查询出所有 {@code Advisor} 类型的 Bean。</p>
 */
public interface BeanFactoryAware {

    void setBeanFactory(BeanFactory beanFactory) throws BeansException;
}
