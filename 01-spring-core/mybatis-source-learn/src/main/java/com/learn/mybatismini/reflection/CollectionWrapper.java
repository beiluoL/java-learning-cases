package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.property.PropertyTokenizer;

import java.util.Collection;

/**
 * 集合包装器：被包装对象本身就是 Collection（如直接传一个 {@code List<Long> ids}）。
 *
 * <p>集合没有"属性"概念，所以除了 {@link #getOriginalObject()} 之外
 * 其它方法都不支持 —— 这与真实 MyBatis 的 {@code CollectionWrapper} 行为一致。
 * 它存在的意义是让 {@code #{list}}、{@code <foreach collection="list">}
 * 能顺利拿到原始集合。
 *
 * @author mybatis-source-learn
 */
public class CollectionWrapper implements ObjectWrapper {

    private final Collection<Object> object;

    @SuppressWarnings("unchecked")
    public CollectionWrapper(Object object) {
        this.object = (Collection<Object>) object;
    }

    @Override
    public Object get(PropertyTokenizer prop) {
        throw new UnsupportedOperationException("集合本身没有属性，无法读取 " + prop.getName());
    }

    @Override
    public void set(PropertyTokenizer prop, Object value) {
        throw new UnsupportedOperationException("集合本身没有属性，无法写入 " + prop.getName());
    }

    @Override
    public boolean hasGetter(String name) {
        return false;
    }

    @Override
    public boolean hasSetter(String name) {
        return false;
    }

    @Override
    public Class<?> getGetterType(String name) {
        throw new UnsupportedOperationException("集合没有属性类型");
    }

    @Override
    public Class<?> getSetterType(String name) {
        throw new UnsupportedOperationException("集合没有属性类型");
    }

    @Override
    public String findProperty(String name, boolean useCamelCaseMapping) {
        return null;
    }

    @Override
    public Object getOriginalObject() {
        return object;
    }
}
