package com.learn.springmini.ioc;

/**
 * 初始化回调接口 —— 对应 Spring 的 {@code InitializingBean}。
 *
 * <p>Bean 的所有属性（依赖）都被容器注入完成之后，容器会回调这个方法。
 * 适合在这里做“依赖齐了才能做的”资源准备、校验等工作。
 * （Spring 中更常用的是 XML 的 {@code init-method} 或注解 {@code @PostConstruct}，
 * 这里用接口方式演示“生命周期回调”的本质。）</p>
 */
public interface InitializingBean {

    /** 属性设置完成后调用。 */
    void afterPropertiesSet() throws Exception;
}
