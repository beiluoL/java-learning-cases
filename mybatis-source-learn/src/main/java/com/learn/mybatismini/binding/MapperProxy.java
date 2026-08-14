package com.learn.mybatismini.binding;

import com.learn.mybatismini.session.SqlSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapper 方法拦截器（对应 MyBatis 的 {@code MapperProxy}）。
 *
 * <h3>这就是"接口没有实现类却能执行 SQL"的全部秘密</h3>
 * <pre>
 *   userMapper.selectById(1L)
 *        │  JDK 动态代理把调用拦到这里
 *        ▼
 *   MapperProxy.invoke(...)
 *        │  用 "接口全限定名 + 方法名" 拼出 statementId
 *        │  把实参交给 ParamNameResolver 包装
 *        ▼
 *   MapperMethod.execute(sqlSession, args)
 *        │  按 SQL 类型调 session.selectOne / insert / ...
 *        ▼
 *   Executor → StatementHandler → JDBC
 * </pre>
 */
public class MapperProxy<T> implements InvocationHandler {

    private final SqlSession sqlSession;

    private final Class<T> mapperInterface;

    /** 方法 -> MapperMethod 缓存（每个方法只解析一次 statementId/返回类型） */
    private final Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<>();

    public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface) {
        this.sqlSession = sqlSession;
        this.mapperInterface = mapperInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object 自身的方法（toString/hashCode/equals）直接走代理对象自身
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        // 普通 Mapper 方法：解析一次、缓存复用，再执行
        // （本简化版聚焦 MyBatis 核心，未实现接口 default 方法转发；若需可加 MethodHandles 支持）
        MapperMethod mapperMethod = methodCache.computeIfAbsent(
                method, m -> new MapperMethod(mapperInterface, m, sqlSession.getConfiguration()));
        return mapperMethod.execute(sqlSession, args);
    }
}
