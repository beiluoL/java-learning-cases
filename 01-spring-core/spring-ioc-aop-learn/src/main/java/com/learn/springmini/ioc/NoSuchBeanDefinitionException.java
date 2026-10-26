package com.learn.springmini.ioc;

/**
 * 当容器中找不到指定名称的 Bean 定义时抛出。
 *
 * <p>对应 Spring 的 {@code NoSuchBeanDefinitionException}。</p>
 */
public class NoSuchBeanDefinitionException extends BeansException {

    public NoSuchBeanDefinitionException(String beanName) {
        super("No bean named '" + beanName + "' is defined");
    }
}
