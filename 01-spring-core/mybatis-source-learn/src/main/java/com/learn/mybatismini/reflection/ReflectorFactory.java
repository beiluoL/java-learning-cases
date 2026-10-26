package com.learn.mybatismini.reflection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reflector 缓存工厂（对应 MyBatis 的 {@code DefaultReflectorFactory}）。
 *
 * <h3>核心价值：一个 Class 只解析一次</h3>
 * <pre>
 *   findForClass(User.class)  第 1 次 -> 真正 new Reflector(User.class)（解析所有方法/字段）
 *   findForClass(User.class)  第 2 次 -> 直接从 ConcurrentHashMap 返回，零开销
 * </pre>
 *
 * <h3>为什么用 computeIfAbsent 而不是 双重检查锁？</h3>
 * {@code ConcurrentHashMap.computeIfAbsent} 本身是原子的（对同一 key 加桶级锁），
 * 既保证"只创建一次"，又比 {@code synchronized} 整个方法粒度更细。
 * 这是 JDK8 之后推荐的缓存写法，MyBatis 源码里也是这么写的。
 *
 * <p>本类是<b>工厂模式 + 享元模式</b>的结合：
 * 工厂负责创建，享元负责让重复的元数据对象被共享复用。
 *
 * @author mybatis-source-learn
 */
public class ReflectorFactory {

    /** 是否启用缓存（真实 MyBatis 允许关闭，便于热部署场景） */
    private boolean classCacheEnabled = true;

    /** Class -> Reflector 的并发安全缓存 */
    private final ConcurrentMap<Class<?>, Reflector> reflectorMap = new ConcurrentHashMap<>();

    public boolean isClassCacheEnabled() {
        return classCacheEnabled;
    }

    public void setClassCacheEnabled(boolean classCacheEnabled) {
        this.classCacheEnabled = classCacheEnabled;
    }

    /**
     * 获取（必要时创建）某个类的 Reflector。
     */
    public Reflector findForClass(Class<?> type) {
        if (classCacheEnabled) {
            return reflectorMap.computeIfAbsent(type, Reflector::new);
        }
        return new Reflector(type);
    }

    /** 当前缓存了多少个类（Demo 里用来展示缓存效果） */
    public int cacheSize() {
        return reflectorMap.size();
    }
}
