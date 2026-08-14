package com.learn.mybatismini.reflection;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象工厂（对应 MyBatis 的 {@code DefaultObjectFactory}）。
 *
 * <h3>它的职责</h3>
 * ResultSetHandler 把一行结果集映射成对象前，得先<b>创建</b>这个对象。
 * 谁来 new？就是它。
 *
 * <h3>为什么要抽象成"工厂"而不是直接 newInstance？</h3>
 * 因为存在两类特殊情况需要定制：
 * <ol>
 *   <li>接口类型：{@code resultType="java.util.List"} 时不能 new 接口，
 *       必须替换成具体实现（List -> ArrayList，Map -> HashMap，Set -> HashSet）；</li>
 *   <li>用户想接管对象创建：比如从对象池取、或者创建后统一注入某些默认值。
 *       真实 MyBatis 允许在 config 里配 {@code <objectFactory>} 覆盖它 —— 这就是
 *       <b>工厂模式提供的扩展点</b>。</li>
 * </ol>
 *
 * @author mybatis-source-learn
 */
public class ObjectFactory {

    /** 接口 -> 默认实现类 的替换表 */
    private static final Map<Class<?>, Class<?>> INTERFACE_DEFAULTS = new HashMap<>();

    static {
        INTERFACE_DEFAULTS.put(List.class, ArrayList.class);
        INTERFACE_DEFAULTS.put(java.util.Collection.class, ArrayList.class);
        INTERFACE_DEFAULTS.put(Iterable.class, ArrayList.class);
        INTERFACE_DEFAULTS.put(Map.class, HashMap.class);
        INTERFACE_DEFAULTS.put(java.util.Set.class, java.util.HashSet.class);
        INTERFACE_DEFAULTS.put(java.util.SortedSet.class, java.util.TreeSet.class);
    }

    /**
     * 用无参构造器创建对象。
     */
    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> type) {
        Class<?> classToCreate = resolveInterface(type);
        try {
            Constructor<?> constructor = classToCreate.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (T) constructor.newInstance();
        } catch (Exception e) {
            throw new ReflectionException("实例化 " + classToCreate.getName() + " 失败。"
                    + "请确认它有无参构造器且不是抽象类/接口。", e);
        }
    }

    /**
     * 把接口类型替换为可实例化的默认实现。
     */
    protected Class<?> resolveInterface(Class<?> type) {
        Class<?> impl = INTERFACE_DEFAULTS.get(type);
        return impl != null ? impl : type;
    }

    /**
     * 是否是集合类型。
     * <p>ResultSetHandler 判断"多行结果要不要装进集合"时会用到。
     */
    public <T> boolean isCollection(Class<T> type) {
        return java.util.Collection.class.isAssignableFrom(type);
    }
}
