package com.learn.springmini.ioc.beans;

import java.util.ArrayList;
import java.util.List;

/**
 * 一组“属性值”的集合 —— 对应一个 Bean 的全部 {@code <property>}。
 *
 * <p>容器在“属性填充（populate）”阶段会遍历它，逐个通过 Setter 注入。</p>
 */
public class PropertyValues {

    private final List<PropertyValue> propertyValueList = new ArrayList<>();

    public void addPropertyValue(PropertyValue pv) {
        propertyValueList.add(pv);
    }

    public List<PropertyValue> getPropertyValues() {
        return propertyValueList;
    }

    public boolean isEmpty() {
        return propertyValueList.isEmpty();
    }

    /** 根据属性名查找，找不到返回 null。 */
    public PropertyValue getPropertyValue(String name) {
        for (PropertyValue pv : propertyValueList) {
            if (pv.getName().equals(name)) {
                return pv;
            }
        }
        return null;
    }
}
