package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;

/**
 * Bean 定义注册表 —— 对应 Spring 的 {@code BeanDefinitionRegistry}。
 *
 * <p>容器内部并不直接持有“对象”，而是先持有“Bean 的定义信息”（{@link BeanDefinition}）：
 * 类是谁、作用域单例还是原型、构造参数有哪些、属性如何注入……
 * 真正创建对象时，再根据这些“配方”去实例化。这才是 IoC 容器灵活、可配置的根本原因。</p>
 */
public interface BeanDefinitionRegistry {

    /** 注册一个 Bean 定义（相当于在 XML 里写了一个 {@code <bean>}）。 */
    void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeansException;

    /** 取出指定名称的 Bean 定义。 */
    BeanDefinition getBeanDefinition(String beanName) throws BeansException;

    /** 是否已注册该名称的 Bean 定义。 */
    boolean containsBeanDefinition(String beanName);

    /** 返回所有已注册 Bean 定义的名称。 */
    String[] getBeanDefinitionNames();
}
