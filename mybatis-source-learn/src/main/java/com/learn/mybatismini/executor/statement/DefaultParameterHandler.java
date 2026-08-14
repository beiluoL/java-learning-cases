package com.learn.mybatismini.executor.statement;

import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.ParameterMapping;
import com.learn.mybatismini.reflection.MetaObject;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.type.JdbcType;
import com.learn.mybatismini.type.TypeHandler;
import com.learn.mybatismini.type.TypeHandlerRegistry;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 默认参数处理器（对应 MyBatis 的 {@code DefaultParameterHandler}）。
 *
 * <h3>取值的"三级路由"是理解参数的关键</h3>
 * 对每一个 {@code #{property}} 占位符，按如下顺序拿到值：
 * <pre>
 *   1. BoundSql 的临时参数（foreach 的 __frch_*）    —— 优先
 *   2. 单值基本类型（Long/Integer/...）               —— 整个对象就是值
 *   3. 用 MetaObject 从入参对象里 getValue(property)  —— 复杂对象/Map
 * </pre>
 * 拿到值后，挑一个 {@link TypeHandler} 完成 "Java 值 -> JDBC setXxx" 的转换
 * （null 值还会按 jdbcType 调 {@code setNull}）。
 */
public class DefaultParameterHandler implements ParameterHandler {

    private final Configuration configuration;

    private final MappedStatement mappedStatement;

    private final Object parameterObject;

    private final BoundSql boundSql;

    private final TypeHandlerRegistry typeHandlerRegistry;

    public DefaultParameterHandler(Configuration configuration, MappedStatement mappedStatement,
                                   Object parameterObject, BoundSql boundSql) {
        this.configuration = configuration;
        this.mappedStatement = mappedStatement;
        this.parameterObject = parameterObject;
        this.boundSql = boundSql;
        this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    }

    @Override
    public Object getParameterObject() {
        return parameterObject;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setParameters(PreparedStatement ps) throws SQLException {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        StringBuilder paramLog = new StringBuilder();
        for (int i = 0; i < mappings.size(); i++) {
            ParameterMapping pm = mappings.get(i);
            String property = pm.getProperty();
            // ----- 1) 决定值从哪来 -----
            Object value;
            if (boundSql.hasAdditionalParameter(property)) {
                value = boundSql.getAdditionalParameter(property);
            } else if (parameterObject == null) {
                value = null;
            } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
                // 单值基本类型：整个对象就是要设的值（如 selectById 的 Long id）
                value = parameterObject;
            } else {
                MetaObject metaObject = configuration.newMetaObject(parameterObject);
                value = metaObject.getValue(property);
            }
            // ----- 2) 挑 TypeHandler 并设参 -----
            Class<?> javaType = pm.getJavaType();
            JdbcType jdbcType = pm.getJdbcType();
            TypeHandler typeHandler;
            if (javaType != null) {
                typeHandler = typeHandlerRegistry.getTypeHandler(javaType, jdbcType);
            } else if (value != null) {
                typeHandler = typeHandlerRegistry.getTypeHandler(value.getClass(), jdbcType);
            } else {
                typeHandler = typeHandlerRegistry.getTypeHandler(Object.class);
            }
            if (typeHandler == null) {
                typeHandler = typeHandlerRegistry.getTypeHandler(Object.class);
            }
            typeHandler.setParameter(ps, i + 1, value, jdbcType);

            if (paramLog.length() > 0) {
                paramLog.append(", ");
            }
            paramLog.append(value).append("(").append(value == null ? "null" : value.getClass().getSimpleName()).append(")");
        }
        MiniLog.sqlParameters(paramLog.toString());
    }
}
