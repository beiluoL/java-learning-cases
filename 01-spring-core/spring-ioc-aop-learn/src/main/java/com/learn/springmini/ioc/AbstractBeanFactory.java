package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象 Bean 工厂 —— 对应 Spring 的 {@code AbstractBeanFactory}。
 *
 * <p>它把“获取 Bean 的通用流程”固定下来，形成模板方法：</p>
 * <pre>
 *   getBean(name)
 *     └─ 先查单例缓存 → 命中直接返回
 *        └─ 否则取出 BeanDefinition，调用 createBean()（由子类实现）去真正创建
 * </pre>
 * 这样既统一了取 Bean 的逻辑，又把“如何创建/注入”的差异留给了子类，
 * 是典型模板方法模式（Template Method）。
 */
public abstract class AbstractBeanFactory implements BeanFactory, SingletonBeanRegistry, BeanDefinitionRegistry {

    /** 单例缓存：名称 → 已创建好的单例对象。 */
    protected final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

    /** Bean 定义表：名称 → BeanDefinition。 */
    protected final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

    // ===================== BeanFactory 接口实现 =====================

    @Override
    public Object getBean(String name) throws BeansException {
        // 1) 单例缓存命中，直接返回（这也是单例“只创建一次”的原因）
        Object sharedInstance = getSingleton(name);
        if (sharedInstance != null) {
            return sharedInstance;
        }
        // 2) 取出定义，交给子类去实例化
        BeanDefinition bd = getBeanDefinition(name);
        return createBean(name, bd);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        Object bean = getBean(name);
        if (requiredType != null && !requiredType.isInstance(bean)) {
            throw new BeansException("Bean '" + name + "' 的类型 "
                    + bean.getClass().getName() + " 与期望的 " + requiredType.getName() + " 不兼容");
        }
        return (T) bean;
    }

    @Override
    public boolean containsBean(String name) {
        return containsSingleton(name) || containsBeanDefinition(name);
    }

    @Override
    public boolean isSingleton(String name) throws BeansException {
        return getBeanDefinition(name).isSingleton();
    }

    @Override
    public boolean isPrototype(String name) throws BeansException {
        return getBeanDefinition(name).isPrototype();
    }

    // ===================== SingletonBeanRegistry 接口实现 =====================

    @Override
    public void registerSingleton(String beanName, Object singleton) {
        singletonObjects.put(beanName, singleton);
    }

    @Override
    public Object getSingleton(String beanName) {
        return singletonObjects.get(beanName);
    }

    @Override
    public boolean containsSingleton(String beanName) {
        return singletonObjects.containsKey(beanName);
    }

    // ===================== BeanDefinitionRegistry 接口实现 =====================

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeansException {
        beanDefinition.validate();
        beanDefinitionMap.put(beanName, beanDefinition);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws BeansException {
        BeanDefinition bd = beanDefinitionMap.get(beanName);
        if (bd == null) {
            throw new NoSuchBeanDefinitionException(beanName);
        }
        return bd;
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return beanDefinitionMap.containsKey(beanName);
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return beanDefinitionMap.keySet().toArray(new String[0]);
    }

    // ===================== 留给子类实现的抽象方法 =====================

    /** 真正创建并返回一个 Bean 实例（含实例化、依赖注入、初始化、后置处理）。 */
    protected abstract Object createBean(String beanName, BeanDefinition beanDefinition) throws BeansException;
}
