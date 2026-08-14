package com.learn.springmini.ioc;

/**
 * Bean 后置处理器 —— 对应 Spring 的 {@code BeanPostProcessor}。
 *
 * <h3>这是连接 IoC 与 AOP 的关键扩展点！</h3>
 * 容器在“实例化 → 依赖注入 → 初始化”之后，会依次调用所有后置处理器的两个钩子：
 * <ul>
 *   <li>{@link #postProcessBeforeInitialization}：初始化之前</li>
 *   <li>{@link #postProcessAfterInitialization}：初始化之后</li>
 * </ul>
 * AOP 的“自动代理”正是在 <b>after</b> 这个钩子里，把原始 Bean 替换成一个代理对象返回，
 * 从而让调用方拿到的是“已经被切面增强过的代理”。
 */
public interface BeanPostProcessor {

    /** 初始化前调用，默认原样返回（子类按需改写）。 */
    default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /** 初始化后调用，默认原样返回。AOP 自动代理在这里把 Bean 换成代理。 */
    default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
}
