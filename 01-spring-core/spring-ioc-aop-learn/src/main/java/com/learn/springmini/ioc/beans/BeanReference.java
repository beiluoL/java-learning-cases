package com.learn.springmini.ioc.beans;

/**
 * Bean 引用 —— 表示一个属性/构造参数“指向另一个 Bean”。
 *
 * <p>XML 中的 {@code ref="userDao"} 在解析后会被包装成 {@code new BeanReference("userDao")}，
 * 容器在遇到它时，会递归调用 {@code getBean("userDao")} 取出那个 Bean 作为依赖注入进来。
 * 这正是“依赖注入（DI）”能自动串联起整个对象图的关键。</p>
 */
public class BeanReference {

    /** 被引用 Bean 的名称。 */
    private final String beanName;

    public BeanReference(String beanName) {
        this.beanName = beanName;
    }

    public String getBeanName() {
        return beanName;
    }

    @Override
    public String toString() {
        return "BeanReference{" + beanName + '}';
    }
}
