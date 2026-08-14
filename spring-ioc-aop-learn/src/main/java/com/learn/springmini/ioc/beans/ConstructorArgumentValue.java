package com.learn.springmini.ioc.beans;

/**
 * 一个“构造器参数值” —— 对应 XML 里的 {@code <constructor-arg>}。
 *
 * <p>与 {@link PropertyValue} 类似，值可以是字面量 String，也可以是 {@link BeanReference}。
 * 通过 {@code index} 指明它对应构造函数的第几个参数（从 0 开始）。</p>
 */
public class ConstructorArgumentValue {

    /** 参数位置（第几个构造参数）。 */
    private final int index;

    /** 原始值：字面量 String 或 BeanReference。 */
    private final Object value;

    public ConstructorArgumentValue(int index, Object value) {
        this.index = index;
        this.value = value;
    }

    public int getIndex() {
        return index;
    }

    public Object getValue() {
        return value;
    }

    public boolean isReference() {
        return value instanceof BeanReference;
    }

    public String getReferencedBeanName() {
        return isReference() ? ((BeanReference) value).getBeanName() : null;
    }
}
