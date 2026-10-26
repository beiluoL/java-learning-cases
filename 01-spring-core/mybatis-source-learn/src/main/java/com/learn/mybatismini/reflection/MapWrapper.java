package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.property.PropertyTokenizer;

import java.util.Map;

/**
 * Map 包装器：把 {@code #{userName}} 翻译成 {@code map.get("userName")}。
 *
 * <h3>它为什么重要？</h3>
 * MyBatis 里有大量场景其实是在操作 Map：
 * <ul>
 *   <li>多参数方法（{@code @Param("min") int min, @Param("max") int max}）
 *       会被 ParamNameResolver 包成 {@code Map{min=…, max=…, param1=…, param2=…}}；</li>
 *   <li>{@code parameterType="map"} 直接传 Map；</li>
 *   <li>动态 SQL 的 DynamicContext 内部上下文也是 Map（存 _parameter、foreach 的 item 等）。</li>
 * </ul>
 * 换句话说：<b>Map 是 MyBatis 内部参数传递的通用载体</b>，MapWrapper 就是它的访问适配器。
 *
 * @author mybatis-source-learn
 */
public class MapWrapper implements ObjectWrapper {

    private final Map<String, Object> map;

    @SuppressWarnings("unchecked")
    public MapWrapper(Object object) {
        this.map = (Map<String, Object>) object;
    }

    @Override
    public Object get(PropertyTokenizer prop) {
        if (prop.getIndex() != null) {
            // 形如 list[0]：先按 name 取出集合，再取索引
            Object collection = map.get(prop.getName());
            return resolveIndexedValue(collection, prop.getIndex());
        }
        return map.get(prop.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveIndexedValue(Object collection, String index) {
        if (collection == null) {
            return null;
        }
        if (collection instanceof java.util.List<?> list) {
            return list.get(Integer.parseInt(index));
        }
        if (collection instanceof Map m) {
            return m.get(index);
        }
        if (collection.getClass().isArray()) {
            return java.lang.reflect.Array.get(collection, Integer.parseInt(index));
        }
        return null;
    }

    @Override
    public void set(PropertyTokenizer prop, Object value) {
        map.put(prop.getName(), value);
    }

    @Override
    public boolean hasGetter(String name) {
        // Map 的语义：key 存在即可读。注意级联时只判断第一段
        return map.containsKey(new PropertyTokenizer(name).getName());
    }

    @Override
    public boolean hasSetter(String name) {
        // Map 永远可写（put 一个新 key 即可）
        return true;
    }

    @Override
    public Class<?> getGetterType(String name) {
        Object value = map.get(new PropertyTokenizer(name).getName());
        // Map 是弱类型的，只能按运行时实际值推断
        return value == null ? Object.class : value.getClass();
    }

    @Override
    public Class<?> getSetterType(String name) {
        return Object.class;
    }

    @Override
    public String findProperty(String name, boolean useCamelCaseMapping) {
        // Map 的 key 必须精确匹配，不做大小写/下划线转换
        return name;
    }

    @Override
    public Object getOriginalObject() {
        return map;
    }
}
