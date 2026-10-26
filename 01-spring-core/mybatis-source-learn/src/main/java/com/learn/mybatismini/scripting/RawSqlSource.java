package com.learn.mybatismini.scripting;

import com.learn.mybatismini.builder.SqlSourceBuilder;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.SqlSource;
import com.learn.mybatismini.mapping.StaticSqlSource;
import com.learn.mybatismini.session.Configuration;

/**
 * 原始（静态）SQL 源（对应 MyBatis 的 {@code RawSqlSource}）。
 *
 * <h3>"解析一次，缓存复用" —— 性能最好的 SQL 形态</h3>
 * 构造时就调用 {@link SqlSourceBuilder} 把 {@code #{}} 转成 {@code ?} 并生成 ParameterMapping，
 * 之后每次 {@code getBoundSql} 只是把结果包一层 {@code BoundSql}（sql 与 mappings 共享不变），
 * 不再做任何字符串解析。这也是为什么"能不用动态标签就别用"。
 */
public class RawSqlSource implements SqlSource {

    private final StaticSqlSource staticSqlSource;

    public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType) {
        SqlSourceBuilder builder = new SqlSourceBuilder(configuration);
        this.staticSqlSource = builder.parse(sql, parameterType, null);
    }

    @Override
    public BoundSql getBoundSql(Object parameterObject) {
        return staticSqlSource.getBoundSql(parameterObject);
    }
}
