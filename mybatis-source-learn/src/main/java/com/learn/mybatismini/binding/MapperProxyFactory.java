package com.learn.mybatismini.binding;

import com.learn.mybatismini.session.SqlSession;

import java.lang.reflect.Proxy;

/**
 * Mapper 代理工厂（对应 MyBatis 的 {@code MapperProxyFactory}）。
 *
 * <p>负责为某个 Mapper 接口创建 JDK 动态代理实例。
 * 工厂模式在这里的意义：把"怎么造代理"封装起来，SqlSession 只管调 {@code newInstance}。
 */
public class MapperProxyFactory<T> {

    private final Class<T> mapperInterface;

    public MapperProxyFactory(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    public T newInstance(SqlSession sqlSession) {
        MapperProxy<T> proxy = new MapperProxy<>(sqlSession, mapperInterface);
        return mapperInterface.cast(Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class[]{mapperInterface},
                proxy));
    }
}
