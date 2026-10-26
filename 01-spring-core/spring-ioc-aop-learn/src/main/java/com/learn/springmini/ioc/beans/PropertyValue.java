package com.learn.springmini.ioc.beans;

/**
 * 一个“属性值” —— 对应 XML 里的 {@code <property>}。
 *
 * <p>属性值有两种来源：</p>
 * <ul>
 *   <li><b>字面量</b>：如 {@code <property name="appName" value="Mini-Spring"/>}，
 *       此时 {@link #value} 是一个 {@link String}。</li>
 *   <li><b>引用其它 Bean</b>：如 {@code <property name="userDao" ref="userDao"/>}，
 *       此时 {@link #value} 是一个 {@link BeanReference}（包裹了被引用 Bean 的名称）。</li>
 * </ul>
 */
public class PropertyValue {

    /** 属性名（对应 JavaBean 的 setter，如 name="appName" → setAppName）。 */
    private final String name;

    /** 原始值：可能是 String 字面量，也可能是 BeanReference 引用。 */
    private final Object value;

    public PropertyValue(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    /** 该属性值是否指向另一个 Bean（而非字面量）。 */
    public boolean isReference() {
        return value instanceof BeanReference;
    }

    /** 若是引用，返回被引用 Bean 的名称。 */
    public String getReferencedBeanName() {
        return isReference() ? ((BeanReference) value).getBeanName() : null;
    }
}
