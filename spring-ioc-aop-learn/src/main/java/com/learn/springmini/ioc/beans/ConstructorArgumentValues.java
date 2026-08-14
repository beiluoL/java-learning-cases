package com.learn.springmini.ioc.beans;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一组“构造器参数值”的集合 —— 对应一个 Bean 的全部 {@code <constructor-arg>}。
 *
 * <p>容器在“实例化”阶段，会按 index 排序后，据此定位并调用匹配的构造函数。</p>
 */
public class ConstructorArgumentValues {

    private final List<ConstructorArgumentValue> argumentValues = new ArrayList<>();

    public void addArgumentValue(ConstructorArgumentValue value) {
        argumentValues.add(value);
    }

    public List<ConstructorArgumentValue> getArgumentValues() {
        return argumentValues;
    }

    public boolean isEmpty() {
        return argumentValues.isEmpty();
    }

    /** 返回按 index 升序排列后的参数（方便逐个对应构造函数形参）。 */
    public List<ConstructorArgumentValue> getSortedValues() {
        List<ConstructorArgumentValue> sorted = new ArrayList<>(argumentValues);
        sorted.sort(Comparator.comparingInt(ConstructorArgumentValue::getIndex));
        return sorted;
    }
}
