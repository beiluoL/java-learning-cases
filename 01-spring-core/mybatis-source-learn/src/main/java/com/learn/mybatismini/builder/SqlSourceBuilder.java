package com.learn.mybatismini.builder;

import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.ParameterMapping;
import com.learn.mybatismini.mapping.StaticSqlSource;
import com.learn.mybatismini.parsing.GenericTokenParser;
import com.learn.mybatismini.type.JdbcType;
import com.learn.mybatismini.type.TypeAliasRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SQL 源构建器（对应 MyBatis 的 {@code SqlSourceBuilder}）。
 *
 * <h3>把 {@code #{}} 文本翻译成"预编译 SQL + 参数映射"</h3>
 * <pre>
 *   输入：INSERT INTO t_user(user_name, age) VALUES(#{userName}, #{age})
 *   过程：用 GenericTokenParser 扫描 #{...}，每个替换成 "?"，
 *        同时按出现顺序生成一个 ParameterMapping(userName / age)
 *   输出：StaticSqlSource("INSERT ... VALUES(?, ?)", [userName, age])
 * </pre>
 *
 * <p>它还解析 {@code #{prop,jdbcType=INTEGER}} 里的可选后缀，
 * 把 {@code javaType / jdbcType} 记录下来交给 TypeHandler 使用。
 */
public class SqlSourceBuilder {

    private final TypeAliasRegistry typeAliasRegistry;

    public SqlSourceBuilder(com.learn.mybatismini.session.Configuration configuration) {
        this.typeAliasRegistry = configuration.getTypeAliasRegistry();
    }

    /**
     * 解析 SQL，产出最终可直接使用的 StaticSqlSource。
     */
    public StaticSqlSource parse(String originalSql, Class<?> parameterType, Object parameterObject) {
        List<ParameterMapping> parameterMappings = new ArrayList<>();
        GenericTokenParser parser = new GenericTokenParser("#{", "}", content -> {
            ParameterMapping mapping = buildParameterMapping(content);
            parameterMappings.add(mapping);
            return "?";
        });
        String sql = parser.parse(originalSql);
        return new StaticSqlSource(sql, parameterMappings);
    }

    /** 解析单个 #{...} 内容（property[, javaType=..., jdbcType=...]） */
    private ParameterMapping buildParameterMapping(String content) {
        String[] parts = content.split(",");
        String property = parts[0].trim();
        Class<?> javaType = null;
        JdbcType jdbcType = null;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String val = part.substring(eq + 1).trim();
                if ("jdbctype".equals(key)) {
                    jdbcType = JdbcType.valueOf(val.toUpperCase(Locale.ROOT));
                } else if ("javatype".equals(key)) {
                    javaType = resolveClass(val);
                }
            }
        }
        ParameterMapping mapping = new ParameterMapping(property);
        if (javaType != null) {
            mapping.setJavaType(javaType);
        }
        if (jdbcType != null) {
            mapping.setJdbcType(jdbcType);
        }
        return mapping;
    }

    private Class<?> resolveClass(String val) {
        try {
            return typeAliasRegistry.resolveAlias(val);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
