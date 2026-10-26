package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;

/**
 * 访问 Bean 容器的根接口 —— 对应 Spring 的 {@code BeanFactory}。
 *
 * <h3>核心思想（IoC 的“控制反转”）</h3>
 * 传统写法里，对象由我们自己 {@code new} 出来，并自己管理依赖；
 * 使用 IoC 容器后，“谁来创建对象、谁把依赖塞进去”这两个控制权被<b>反转</b>给了容器。
 * 调用方只需要向容器要一个 Bean（{@code getBean}），至于对象怎么造、依赖从哪来，全部由容器决定。
 *
 * <p>本接口只保留最常用的几个方法，足以演示 IoC 的本质。</p>
 */
public interface BeanFactory {

    /** 根据 Bean 名称获取一个实例（单例返回同一对象，原型每次新建）。 */
    Object getBean(String name) throws BeansException;

    /** 根据名称获取实例，并强转为指定类型（类型不匹配会抛异常）。 */
    <T> T getBean(String name, Class<T> requiredType) throws BeansException;

    /** 容器里是否包含该名称的 Bean 定义或单例。 */
    boolean containsBean(String name);

    /** 该 Bean 是否为“单例”（整个容器共享一个实例）。 */
    boolean isSingleton(String name) throws BeansException;

    /** 该 Bean 是否为“原型”（每次获取都新建一个实例）。 */
    boolean isPrototype(String name) throws BeansException;
}
