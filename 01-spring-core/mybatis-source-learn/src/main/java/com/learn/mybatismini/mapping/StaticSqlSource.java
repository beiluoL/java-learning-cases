package com.learn.mybatismini.mapping;

import java.util.List;

/**
 * 静态 SQL 源：SQL 文本与参数映射在解析期就已确定，运行时直接包成 BoundSql 返回。
 * （对应 MyBatis 的 {@code StaticSqlSource}）
 *
 * <p>它是所有 SqlSource 的"终点"：
 * <pre>
 *   RawSqlSource      解析一次 -> StaticSqlSource（缓存复用）
 *   DynamicSqlSource  每次拼完 -> 临时 StaticSqlSource（用完即弃）
 * </pre>
 *
 * @author mybatis-source-learn
 */
public class StaticSqlSource implements SqlSource {

    private final String sql;

    private final List<ParameterMapping> parameterMappings;

    public StaticSqlSource(String sql, List<ParameterMapping> parameterMappings) {
        this.sql = sql;
        this.parameterMappings = parameterMappings;
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        // 每次调用返回新的 BoundSql 对象（因为 parameterObject 不同），
        // 但 sql 字符串与 mappings 列表是共享的不可变数据 —— 这样既线程安全又省内存。
        return new BoundSql(sql, parameterMappings, parameterObject);
    }

    public String getSql() {
        return sql;
    }

    public List<ParameterMapping> getParameterMappings() {
        return parameterMappings;
    }
}
