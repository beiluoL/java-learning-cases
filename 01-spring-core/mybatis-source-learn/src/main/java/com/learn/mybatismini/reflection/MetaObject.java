package com.learn.mybatismini.reflection;

import com.learn.mybatismini.reflection.property.PropertyTokenizer;

import java.util.Collection;
import java.util.Map;

/**
 * 对象元数据 —— <b>MyBatis 反射体系的门面（Facade）</b>。
 * （对应 MyBatis 的 {@code org.apache.ibatis.reflection.MetaObject}）
 *
 * <h2>一句话理解：给任何对象套上"统一的属性读写遥控器"</h2>
 * <pre>
 *   MetaObject mo = MetaObject.forObject(user, factory);
 *   mo.getValue("userName");           // 等价于 user.getUserName()
 *   mo.setValue("address.city", "深圳"); // 等价于 user.getAddress().setCity("深圳")
 *   mo.setValue("orders[0].amount", 9); // 等价于 user.getOrders().get(0).setAmount(9)
 * </pre>
 * 不管里面装的是 JavaBean、Map 还是 Collection，调用方式完全一致。
 *
 * <h3>在 MyBatis 中的三大用武之地</h3>
 * <ol>
 *   <li><b>参数设置</b>：DefaultParameterHandler 用它从入参对象里取出 {@code #{userName}} 的值；</li>
 *   <li><b>结果映射</b>：DefaultResultSetHandler 用它把结果集的列值塞进实体属性；</li>
 *   <li><b>主键回填</b>：Jdbc3KeyGenerator 用它把自增主键 set 回入参对象；</li>
 * </ol>
 * 另外 Configuration 解析 {@code <setting>} 时也是用它把值 set 到自己的字段上。
 *
 * <h3>级联属性的实现精髓：递归 + 自动补齐中间对象</h3>
 * {@code setValue("address.city", v)} 时如果 {@code address} 还是 null，
 * MetaObject 会自动 new 一个 Address 塞进去再继续下钻（见 {@link #setValue}）。
 * 这个"自动实例化中间节点"的能力，是 MyBatis 能做嵌套结果映射的前提。
 *
 * @author mybatis-source-learn
 */
public class MetaObject {

    /** 原始对象 */
    private final Object originalObject;

    /** 按类型选出的包装器（Bean / Map / Collection） */
    private final ObjectWrapper objectWrapper;

    private final ObjectFactory objectFactory;

    private final ReflectorFactory reflectorFactory;

    private MetaObject(Object object, ObjectFactory objectFactory, ReflectorFactory reflectorFactory) {
        this.originalObject = object;
        this.objectFactory = objectFactory;
        this.reflectorFactory = reflectorFactory;

        // ★ 关键分派逻辑：根据对象真实类型挑选合适的 Wrapper（适配器模式的落点）
        if (object instanceof ObjectWrapper wrapper) {
            this.objectWrapper = wrapper;
        } else if (object instanceof Map) {
            this.objectWrapper = new MapWrapper(object);
        } else if (object instanceof Collection) {
            this.objectWrapper = new CollectionWrapper(object);
        } else {
            this.objectWrapper = new BeanWrapper(object, reflectorFactory);
        }
    }

    /**
     * 工厂方法。传入 null 时返回 {@link NullObject} 的元数据，
     * 避免调用方到处判空 —— 这是<b>空对象模式（Null Object Pattern）</b>。
     */
    public static MetaObject forObject(Object object, ObjectFactory objectFactory,
                                       ReflectorFactory reflectorFactory) {
        if (object == null) {
            return new MetaObject(NullObject.INSTANCE, objectFactory, reflectorFactory);
        }
        return new MetaObject(object, objectFactory, reflectorFactory);
    }

    public Object getOriginalObject() {
        return originalObject;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public ReflectorFactory getReflectorFactory() {
        return reflectorFactory;
    }

    // ==================================================================
    // 核心读写
    // ==================================================================

    /**
     * 按属性表达式取值，支持级联与索引。
     *
     * <p>执行过程（以 {@code "address.city"} 为例）：
     * <pre>
     *   1. 分词 -> name=address, children=city
     *   2. 取出 address 的值，包装成新的 MetaObject
     *   3. 递归调用 metaValue.getValue("city")
     * </pre>
     * 中途遇到 null 就返回 null（不抛 NPE），这点对动态 SQL 的 {@code <if test="a.b != null">} 很重要。
     */
    public Object getValue(String name) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (prop.hasNext()) {
            MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
            if (metaValue.getOriginalObject() == NullObject.INSTANCE) {
                // 中间节点为 null，整体返回 null
                return null;
            }
            return metaValue.getValue(prop.getChildren());
        }
        return objectWrapper.get(prop);
    }

    /**
     * 按属性表达式设值，支持级联与自动补齐中间对象。
     */
    public void setValue(String name, Object value) {
        PropertyTokenizer prop = new PropertyTokenizer(name);
        if (prop.hasNext()) {
            MetaObject metaValue = metaObjectForProperty(prop.getIndexedName());
            if (metaValue.getOriginalObject() == NullObject.INSTANCE) {
                if (value == null) {
                    // 中间节点为 null 且要设的值也是 null，无需创建对象，直接返回
                    return;
                }
                // ★ 自动补齐：new 一个中间对象塞进去，再继续下钻
                Class<?> type = objectWrapper.getSetterType(prop.getName());
                Object newObject = objectFactory.create(type);
                objectWrapper.set(prop, newObject);
                metaValue = MetaObject.forObject(newObject, objectFactory, reflectorFactory);
            }
            metaValue.setValue(prop.getChildren(), value);
        } else {
            objectWrapper.set(prop, value);
        }
    }

    /** 取某属性的值并包装成 MetaObject（下钻用） */
    public MetaObject metaObjectForProperty(String name) {
        Object value = getValue0(name);
        return MetaObject.forObject(value, objectFactory, reflectorFactory);
    }

    /** 单层取值（不递归），供 metaObjectForProperty 使用 */
    private Object getValue0(String name) {
        return objectWrapper.get(new PropertyTokenizer(name));
    }

    // ==================================================================
    // 能力查询
    // ==================================================================

    public boolean hasGetter(String name) {
        return objectWrapper.hasGetter(name);
    }

    public boolean hasSetter(String name) {
        return objectWrapper.hasSetter(name);
    }

    public Class<?> getSetterType(String name) {
        return objectWrapper.getSetterType(name);
    }

    public Class<?> getGetterType(String name) {
        return objectWrapper.getGetterType(name);
    }

    /**
     * 把列名映射成属性名。
     *
     * @param useCamelCaseMapping true 时 user_name -> userName
     */
    public String findProperty(String name, boolean useCamelCaseMapping) {
        return objectWrapper.findProperty(name, useCamelCaseMapping);
    }

    /**
     * 空对象占位符（空对象模式）。
     * <p>好处：{@code getValue("a.b.c")} 中间任意层为 null 时，
     * 调用链不会崩，只是最终返回 null。
     */
    static final class NullObject {
        static final NullObject INSTANCE = new NullObject();

        private NullObject() {
        }

        @Override
        public String toString() {
            return "null";
        }
    }
}
