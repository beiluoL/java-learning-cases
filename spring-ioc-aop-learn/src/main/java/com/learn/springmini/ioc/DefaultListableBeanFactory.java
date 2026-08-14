package com.learn.springmini.ioc;

import com.learn.springmini.ioc.beans.BeanDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认可列举 Bean 工厂 —— 对应 Spring 的 {@code DefaultListableBeanFactory}。
 *
 * <p>它把前面几层拼装起来，并提供两项关键能力：</p>
 * <ul>
 *   <li>{@link #getBeansOfType}：按类型批量查 Bean（AOP 自动代理靠它找 Advisor）。</li>
 *   <li>{@link #registerBeanPostProcessors} / {@link #preInstantiateSingletons}：
 *       容器启动时先把“后置处理器”准备好，再一次性把所有单例实例化（这是 {@code refresh()} 的简化版）。</li>
 * </ul>
 */
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
        implements ListableBeanFactory {

    // ===================== ListableBeanFactory 实现 =====================

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (String beanName : getBeanDefinitionNames()) {
            BeanDefinition bd = getBeanDefinition(beanName);
            Class<?> beanClass = resolveBeanClass(bd);
            if (type.isAssignableFrom(beanClass)) {
                // 触发实例化（单例会被缓存，下次直接命中）
                T bean = getBean(beanName, type);
                result.put(beanName, bean);
            }
        }
        return result;
    }

    // ===================== 容器启动的两步关键流程 =====================

    /**
     * 找出所有实现了 {@link BeanPostProcessor} 的 Bean，实例化并注册到容器。
     * 必须在“预实例化单例”之前完成，否则 AOP 代理等扩展无法生效。
     */
    public void registerBeanPostProcessors() {
        Map<String, BeanPostProcessor> bppBeans = getBeansOfType(BeanPostProcessor.class);
        for (BeanPostProcessor bpp : bppBeans.values()) {
            addBeanPostProcessor(bpp);
        }
    }

    /**
     * 预实例化所有非懒加载的单例 Bean（对应 Spring 的 {@code finishBeanFactoryInitialization}）。
     * 这一步会触发完整的“实例化 → 注入 → 初始化 → 后置处理（AOP 织入）”流程。
     */
    public void preInstantiateSingletons() {
        for (String beanName : getBeanDefinitionNames()) {
            BeanDefinition bd = getBeanDefinition(beanName);
            if (bd.isSingleton() && !bd.isLazyInit()) {
                getBean(beanName);
            }
        }
    }
}
