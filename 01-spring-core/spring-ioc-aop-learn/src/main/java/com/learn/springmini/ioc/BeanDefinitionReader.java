package com.learn.springmini.ioc;

/**
 * Bean 定义读取器 —— 对应 Spring 的 {@code BeanDefinitionReader}。
 *
 * <p>职责：把“外部配置”（XML / 注解 / JavaConfig）解析成容器能理解的 {@link com.learn.springmini.ioc.beans.BeanDefinition}，
 * 再注册进 {@link BeanDefinitionRegistry}。
 * 这里只实现最经典、最易读的 XML 形式。</p>
 */
public interface BeanDefinitionReader {

    /** 从指定位置（classpath 资源路径）加载并注册 Bean 定义。 */
    void loadBeanDefinitions(String location) throws BeansException;
}
