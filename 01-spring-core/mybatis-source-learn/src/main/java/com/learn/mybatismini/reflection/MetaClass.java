package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.property.PropertyTokenizer;

/**
 * 类级元数据（对应 MyBatis 的 {@code MetaClass}）。
 *
 * <h3>MetaClass 与 MetaObject 的区别（面试常问）</h3>
 * <pre>
 *   MetaClass  ：面向 <b>Class</b>，做"静态"能力判断 —— 这个类有没有 xxx.yyy 属性？类型是什么？
 *                （不需要真实对象，因此可以在配置解析阶段用）
 *   MetaObject ：面向 <b>实例</b>，做"动态"读写 —— 真的把值取出来 / 塞进去。
 * </pre>
 * SqlSourceBuilder 在解析 {@code #{user.address.city}} 时要校验属性是否存在、
 * 推断 javaType 好挑 TypeHandler，此时还没有任何实例，只能用 MetaClass。
 *
 * @author mybatis-source-learn
 */
public class MetaClass {

    private final ReflectorFactory reflectorFactory;
    private final Reflector reflector;

    private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
        this.reflectorFactory = reflectorFactory;
        this.reflector = reflectorFactory.findForClass(type);
    }

    public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
        return new MetaClass(type, reflectorFactory);
    }

    /** 获取某属性类型对应的 MetaClass（用于级联下钻） */
    public MetaClass metaClassForProperty(String name) {
        Class<?> propType = reflector.getGetterType(name);
        return MetaClass.forClass(propType, reflectorFactory);
    }

    /**
     * 递归判断级联属性是否可读，如 {@code user.address.city}。
     */
    public boolean hasGetter(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (!prop.hasNext()) {
            return reflector.hasGetter(prop.getName());
        }
        if (!reflector.hasGetter(prop.getName())) {
            return false;
        }
        return metaClassForProperty(prop.getName()).hasGetter(prop.getChildren());
    }

    /** 递归判断级联属性是否可写 */
    public boolean hasSetter(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (!prop.hasNext()) {
            return reflector.hasSetter(prop.getName());
        }
        if (!reflector.hasGetter(prop.getName())) {
            return false;
        }
        return metaClassForProperty(prop.getName()).hasSetter(prop.getChildren());
    }

    /**
     * 推断级联属性的最终类型，如 {@code user.address.city} -> String。
     * <p>DefaultParameterHandler 需要它来选 TypeHandler。
     */
    public Class<?> getGetterType(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (!prop.hasNext()) {
            return reflector.getGetterType(prop.getName());
        }
        return metaClassForProperty(prop.getName()).getGetterType(prop.getChildren());
    }

    public Class<?> getSetterType(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (!prop.hasNext()) {
            return reflector.getSetterType(prop.getName());
        }
        return metaClassForProperty(prop.getName()).getSetterType(prop.getChildren());
    }

    public String findProperty(String name) {
        return reflector.findPropertyName(name);
    }

    public String[] getGetterNames() {
        return reflector.getGetablePropertyNames();
    }

    public String[] getSetterNames() {
        return reflector.getSetablePropertyNames();
    }

    public boolean hasDefaultConstructor() {
        return reflector.hasDefaultConstructor();
    }
}
