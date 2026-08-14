package com.learn.springmini.ioc;

/**
 * 单例注册表 —— 对应 Spring 的 {@code SingletonBeanRegistry}。
 *
 * <p>容器需要一块“缓存区”来存放已经创建好的单例对象，避免重复创建。
 * 这块缓存就是 {@code singletonObjects}。BeanFactory 在返回单例前会先查这里。</p>
 */
public interface SingletonBeanRegistry {

    /** 把一个已经创建好的单例对象放进缓存（按名称索引）。 */
    void registerSingleton(String beanName, Object singleton);

    /** 从缓存中取出单例对象，没有则返回 null。 */
    Object getSingleton(String beanName);

    /** 缓存中是否已存在该单例。 */
    boolean containsSingleton(String beanName);
}
