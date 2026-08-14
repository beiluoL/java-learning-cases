package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;

import java.util.Map;

/**
 * 类路径 XML 应用上下文 —— 对应 Spring 的 {@code ClassPathXmlApplicationContext}。
 *
 * <p>对初学者而言，这是整个 IoC 容器的“门面（Facade）”：</p>
 * <ol>
 *   <li>读取 classpath 下的 XML；</li>
 *   <li>解析成 BeanDefinition 注册进工厂；</li>
 *   <li>调用 {@code refresh()} 启动容器（注册后置处理器 + 预实例化单例）；</li>
 *   <li>之后随处 {@code getBean()} 即可拿到被完整注入、甚至被 AOP 增强过的 Bean。</li>
 * </ol>
 */
public class ClassPathXmlApplicationContext
        implements ListableBeanFactory, BeanDefinitionRegistry, SingletonBeanRegistry {

    /** 真正干活的底层容器。 */
    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();

    /**
     * 构造即启动：传入一个或多个 classpath 下的 XML 路径（如 "aop-demo.xml"）。
     */
    public ClassPathXmlApplicationContext(String... configLocations) {
        loadBeanDefinitions(configLocations);
        refresh();
    }

    // ===================== 启动流程 =====================

    private void loadBeanDefinitions(String[] configLocations) {
        BeanDefinitionReader reader = new XmlBeanDefinitionReader(beanFactory);
        for (String location : configLocations) {
            reader.loadBeanDefinitions(location);
        }
    }

    /** 容器启动核心（对应 Spring 的 AbstractApplicationContext.refresh 的简化版）。 */
    private void refresh() {
        // 1) 先把 BeanPostProcessor 类型的 Bean 实例化并注册（AOP 自动代理器在此就位）
        beanFactory.registerBeanPostProcessors();
        // 2) 再把所有非懒加载单例实例化（此时 AOP 会自动织入匹配的 Bean）
        beanFactory.preInstantiateSingletons();
    }

    // ===================== 委托给底层工厂 =====================

    @Override
    public Object getBean(String name) throws BeansException {
        return beanFactory.getBean(name);
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return beanFactory.getBean(name, requiredType);
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        return beanFactory.getBeansOfType(type);
    }

    @Override
    public boolean containsBean(String name) {
        return beanFactory.containsBean(name);
    }

    @Override
    public boolean isSingleton(String name) throws BeansException {
        return beanFactory.isSingleton(name);
    }

    @Override
    public boolean isPrototype(String name) throws BeansException {
        return beanFactory.isPrototype(name);
    }

    @Override
    public void registerSingleton(String beanName, Object singleton) {
        beanFactory.registerSingleton(beanName, singleton);
    }

    @Override
    public Object getSingleton(String beanName) {
        return beanFactory.getSingleton(beanName);
    }

    @Override
    public boolean containsSingleton(String beanName) {
        return beanFactory.containsSingleton(beanName);
    }

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeansException {
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws BeansException {
        return beanFactory.getBeanDefinition(beanName);
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return beanFactory.containsBeanDefinition(beanName);
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return beanFactory.getBeanDefinitionNames();
    }

    /** 暴露底层工厂，便于扩展（如手动注册 BeanPostProcessor）。 */
    public DefaultListableBeanFactory getBeanFactory() {
        return beanFactory;
    }
}
