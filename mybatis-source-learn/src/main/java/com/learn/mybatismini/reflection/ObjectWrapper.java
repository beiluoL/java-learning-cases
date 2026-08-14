package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.property.PropertyTokenizer;

/**
 * 对象包装器接口（对应 MyBatis 的 {@code ObjectWrapper}）。
 *
 * <h3>为什么需要这层抽象？</h3>
 * MyBatis 的入参可能是三种完全不同的形态：
 * <pre>
 *   1) JavaBean            user.getUserName()        -> BeanWrapper
 *   2) Map                 map.get("userName")       -> MapWrapper
 *   3) Collection/数组      list.get(0)               -> CollectionWrapper
 * </pre>
 * 但 {@code #{userName}} 这个写法必须对三者都成立。
 * 于是抽象出统一接口，让上层 {@link MetaObject} 只面向接口编程，
 * 具体取值方式由不同 Wrapper 实现 —— 这正是<b>适配器模式 + 策略模式</b>。
 *
 * <p>这就是为什么你既能 {@code selectOne(id, user)} 传实体对象，
 * 也能直接传 Map，甚至多参数被框架包装成 Map 之后依然能正常取值。
 *
 * @author mybatis-source-learn
 */
public interface ObjectWrapper {

    /** 取属性值（prop 是已分词的当前段） */
    Object get(PropertyTokenizer prop);

    /** 设属性值 */
    void set(PropertyTokenizer prop, Object value);

    /** 属性是否可读 */
    boolean hasGetter(String name);

    /** 属性是否可写 */
    boolean hasSetter(String name);

    /** 属性的读类型 */
    Class<?> getGetterType(String name);

    /** 属性的写类型 */
    Class<?> getSetterType(String name);

    /**
     * 忽略大小写/下划线地找到真实属性名。
     *
     * @param name                属性名或列名
     * @param useCamelCaseMapping 是否把下划线转驼峰（user_name -> userName）
     */
    String findProperty(String name, boolean useCamelCaseMapping);

    /** 被包装的原始对象 */
    Object getOriginalObject();
}
