package com.learn.mybatismini.binding;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.SqlSession;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper 注册中心（对应 MyBatis 的 {@code MapperRegistry}）。
 *
 * <p>每个被注册过的 Mapper 接口，都对应一个 {@link MapperProxyFactory}。
 * 当 {@code sqlSession.getMapper(UserMapper.class)} 时，由工厂用 JDK 动态代理
 * 造出一个"看似实现了接口、实则把方法转发给 SqlSession"的代理对象。
 */
public class MapperRegistry {

    private final Configuration config;

    private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

    public MapperRegistry(Configuration config) {
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
        MapperProxyFactory<T> factory = (MapperProxyFactory<T>) knownMappers.get(type);
        if (factory == null) {
            throw new PersistenceException("未注册 Mapper：" + type.getName()
                    + "（请检查配置 <mappers> 是否包含它，或注解式 Mapper 是否加 @CacheNamespace 并被扫描）");
        }
        return factory.newInstance(sqlSession);
    }

    public <T> void addMapper(Class<T> type) {
        if (!type.isInterface()) {
            return;
        }
        if (hasMapper(type)) {
            throw new PersistenceException("Mapper " + type + " 已重复注册");
        }
        knownMappers.put(type, new MapperProxyFactory<>(type));
    }

    public boolean hasMapper(Class<?> type) {
        return knownMappers.containsKey(type);
    }
}
