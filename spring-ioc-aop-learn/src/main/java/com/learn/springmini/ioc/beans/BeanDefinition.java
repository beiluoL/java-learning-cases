package com.learn.springmini.ioc.beans;

import com.learn.springmini.ioc.BeansException;

/**
 * Bean 定义 —— 对应 Spring 的 {@code BeanDefinition}。
 *
 * <p>它是 Bean 的“配方/蓝图”，记录了创建一个 Bean 所需的全部元信息：</p>
 * <ul>
 *   <li>{@code beanClassName}：要实例化的类全限定名</li>
 *   <li>{@code scope}：作用域，{@code singleton}（单例，默认）或 {@code prototype}（原型）</li>
 *   <li>{@code constructorArgumentValues}：构造器注入的参数</li>
 *   <li>{@code propertyValues}：Setter 注入的属性</li>
 * </ul>
 *
 * <p>容器不会一上来就 {@code new} 对象，而是先把这些信息解析并保存为 BeanDefinition，
 * 等到真正需要时（或容器启动时）再按图施工。这就是“配置驱动”的核心。</p>
 */
public class BeanDefinition {

    /** 默认作用域：单例。 */
    public static final String SCOPE_SINGLETON = "singleton";
    /** 原型作用域：每次获取都新建。 */
    public static final String SCOPE_PROTOTYPE = "prototype";

    /** 待实例化类的全限定名，如 "com.learn.springmini.demo.service.UserServiceImpl"。 */
    private String beanClassName;

    /** 作用域，默认单例。 */
    private String scope = SCOPE_SINGLETON;

    /** 是否懒加载（容器启动时不提前实例化，用到才创建）。 */
    private boolean lazyInit = false;

    /** 初始化方法名（对应 init-method，本项目仅做接口占位）。 */
    private String initMethodName;

    /** 构造器注入参数集合。 */
    private ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();

    /** Setter 注入属性集合。 */
    private PropertyValues propertyValues = new PropertyValues();

    public BeanDefinition() {
    }

    public BeanDefinition(String beanClassName) {
        this.beanClassName = beanClassName;
    }

    public String getBeanClassName() {
        return beanClassName;
    }

    public void setBeanClassName(String beanClassName) {
        this.beanClassName = beanClassName;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    /** 是否为单例（便捷判断）。 */
    public boolean isSingleton() {
        return SCOPE_SINGLETON.equals(scope);
    }

    /** 是否为原型（便捷判断）。 */
    public boolean isPrototype() {
        return SCOPE_PROTOTYPE.equals(scope);
    }

    public boolean isLazyInit() {
        return lazyInit;
    }

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }

    public String getInitMethodName() {
        return initMethodName;
    }

    public void setInitMethodName(String initMethodName) {
        this.initMethodName = initMethodName;
    }

    public ConstructorArgumentValues getConstructorArgumentValues() {
        return constructorArgumentValues;
    }

    public void setConstructorArgumentValues(ConstructorArgumentValues constructorArgumentValues) {
        this.constructorArgumentValues = constructorArgumentValues;
    }

    public PropertyValues getPropertyValues() {
        return propertyValues;
    }

    public void setPropertyValues(PropertyValues propertyValues) {
        this.propertyValues = propertyValues;
    }

    /** 校验必要字段是否齐全，避免后续实例化时才报错。 */
    public void validate() throws BeansException {
        if (beanClassName == null || beanClassName.trim().isEmpty()) {
            throw new BeansException("BeanDefinition 缺少 beanClassName");
        }
    }
}
