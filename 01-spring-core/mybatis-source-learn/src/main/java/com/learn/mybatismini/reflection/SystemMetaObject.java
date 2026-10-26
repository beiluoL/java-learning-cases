package com.learn.mybatismini.reflection;

/**
 * 全局共享的默认工厂 + 便捷入口（对应 MyBatis 的 {@code SystemMetaObject}）。
 *
 * <p>框架内部很多地方只是想临时包装个对象读写属性，没必要各自 new 工厂
 * （更重要的是：各自 new 会导致 Reflector 缓存分散、失去缓存意义）。
 * 于是提供一组 static 单例。
 *
 * @author mybatis-source-learn
 */
public final class SystemMetaObject {

    /** 全局默认对象工厂 */
    public static final ObjectFactory DEFAULT_OBJECT_FACTORY = new ObjectFactory();

    /** 全局默认反射工厂（所有 Reflector 缓存都集中在这里） */
    public static final ReflectorFactory DEFAULT_REFLECTOR_FACTORY = new ReflectorFactory();

    private SystemMetaObject() {
    }

    /** 用全局默认工厂包装对象 */
    public static MetaObject forObject(Object object) {
        return MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_REFLECTOR_FACTORY);
    }
}
