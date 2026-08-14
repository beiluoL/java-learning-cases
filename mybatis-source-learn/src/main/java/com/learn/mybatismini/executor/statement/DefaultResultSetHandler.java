package com.learn.mybatismini.executor.statement;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.ResultMap;
import com.learn.mybatismini.mapping.ResultMapping;
import com.learn.mybatismini.reflection.MetaObject;
import com.learn.mybatismini.reflection.Reflector;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.type.JdbcType;
import com.learn.mybatismini.type.TypeHandler;
import com.learn.mybatismini.type.TypeHandlerRegistry;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认结果集处理器（对应 MyBatis 的 {@code DefaultResultSetHandler}）。
 *
 * <h3>结果映射的两条路线</h3>
 * <pre>
 *   路线 A 自动映射（resultType="User"，无 resultMap）
 *       列名 -> 属性名（可选 下划线转驼峰）-> TypeHandler 取值 -> MetaObject.setValue
 *
 *   路线 B 显式映射（resultMap="userResultMap"）
 *       按 <result column="user_name" property="userName"/> 逐项取值
 * </pre>
 *
 * <p>关键细节：H2（MODE=MySQL）返回的列标签是大写（{@code USER_NAME}），
 * 所以"列名 -> 属性名"必须大小写不敏感，并在开启 {@code mapUnderscoreToCamelCase} 时
 * 把 {@code USER_NAME -> userName}。这正是 {@link Reflector#findPropertyName} 存在的意义。
 */
public class DefaultResultSetHandler implements ResultSetHandler {

    private final Executor executor;

    private final MappedStatement mappedStatement;

    private final Configuration configuration;

    private final BoundSql boundSql;

    private final TypeHandlerRegistry typeHandlerRegistry;

    public DefaultResultSetHandler(Executor executor, MappedStatement mappedStatement,
                                   Configuration configuration, BoundSql boundSql) {
        this.executor = executor;
        this.mappedStatement = mappedStatement;
        this.configuration = configuration;
        this.boundSql = boundSql;
        this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> handleResultSets(Statement stmt) throws SQLException {
        List<Object> results = new ArrayList<>();
        ResultSet rs = stmt.getResultSet();
        if (rs != null) {
            try (ResultSet closeable = rs) {
                handleResultSet(rs, results);
            }
        }
        MiniLog.sqlTotal(results.size());
        return (List<E>) results;
    }

    private void handleResultSet(ResultSet rs, List<Object> results) throws SQLException {
        ResultMap resultMap = mappedStatement.getResultMap();
        Class<?> resultType = (resultMap != null) ? resultMap.getType() : Object.class;

        // 路线 A：结果类型是"简单类型"（int/String/Long...）—— 直接取第 1 列
        if (resultType != null && typeHandlerRegistry.hasTypeHandler(resultType)) {
            TypeHandler<?> scalarHandler = typeHandlerRegistry.getTypeHandler(resultType);
            while (rs.next()) {
                results.add(scalarHandler.getResult(rs, 1));
            }
            return;
        }

        // 路线 B：复杂对象（User/Order...）—— 逐列映射到属性
        handleBean(rs, resultMap, resultType, results);
    }

    private void handleBean(ResultSet rs, ResultMap resultMap, Class<?> resultType,
                            List<Object> results) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int colCount = md.getColumnCount();
        Reflector reflector = configuration.getReflectorFactory().findForClass(resultType);
        boolean explicit = resultMap != null && resultMap.hasExplicitMappings();

        while (rs.next()) {
            Object row = configuration.getObjectFactory().create(resultType);
            MetaObject metaRow = configuration.newMetaObject(row);
            for (int i = 1; i <= colCount; i++) {
                String columnLabel = md.getColumnLabel(i);
                PropertyColumn pc = resolveProperty(columnLabel, resultMap, reflector, explicit);
                if (pc == null) {
                    continue; // 列没有对应属性，跳过（结果集多出的列）
                }
                TypeHandler<?> handler = typeHandlerRegistry.getTypeHandler(pc.javaType, JdbcType.forCode(md.getColumnType(i)));
                if (handler == null) {
                    handler = typeHandlerRegistry.getTypeHandler(pc.javaType);
                }
                if (handler == null) {
                    handler = typeHandlerRegistry.getTypeHandler(Object.class);
                }
                Object value = handler.getResult(rs, i);
                metaRow.setValue(pc.property, value);
            }
            results.add(row);
        }
    }

    /**
     * 解析"结果集某列 -> 实体哪个属性 + 什么 Java 类型"。
     */
    private PropertyColumn resolveProperty(String columnLabel, ResultMap resultMap,
                                            Reflector reflector, boolean explicit) {
        if (explicit) {
            // 显式映射：按 <result column="..." property="..."/> 精确匹配（大小写不敏感）
            for (ResultMapping rm : resultMap.getResultMappings()) {
                if (rm.getColumn().equalsIgnoreCase(columnLabel)) {
                    Class<?> javaType = (rm.getJavaType() != null)
                            ? rm.getJavaType()
                            : reflector.getSetterType(rm.getProperty());
                    return new PropertyColumn(rm.getProperty(), javaType);
                }
            }
            return null;
        }
        // 自动映射：user_name -> userName
        String prop = configuration.isMapUnderscoreToCamelCase() ? underscoreToCamel(columnLabel) : columnLabel;
        String real = reflector.findPropertyName(prop);
        if (real == null) {
            real = reflector.findPropertyName(columnLabel);
        }
        if (real == null) {
            return null;
        }
        return new PropertyColumn(real, reflector.getSetterType(real));
    }

    /** 下划线命名 -> 驼峰命名（USER_NAME -> userName） */
    private static String underscoreToCamel(String name) {
        if (name == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 列 -> 属性 + Java 类型 的临时载体 */
    private record PropertyColumn(String property, Class<?> javaType) {
    }
}
