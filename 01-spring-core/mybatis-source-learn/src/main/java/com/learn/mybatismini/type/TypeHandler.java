package com.learn.mybatismini.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 类型处理器接口 —— <b>Java 类型与 JDBC 类型之间的双向翻译官</b>。
 * （对应 MyBatis 的 {@code org.apache.ibatis.type.TypeHandler}）
 *
 * <h2>为什么 MyBatis 需要它？</h2>
 * JDBC 的 API 是"按类型分方法"的，非常笨重：
 * <pre>
 *   写：ps.setLong(1, x) / ps.setString(2, y) / ps.setTimestamp(3, z) / ps.setNull(4, type)
 *   读：rs.getLong("id") / rs.getString("name") / rs.getTimestamp("time")
 * </pre>
 * 但框架在运行时只知道"这个属性是 Long 类型"，不可能写死 if-else 覆盖所有类型。
 * 于是抽象出 TypeHandler，把"某个 Java 类型怎么读写"封装成一个个独立的小策略，
 * 再由 {@link TypeHandlerRegistry} 按 Java 类型查表分派。
 *
 * <h3>这是典型的策略模式 + 注册表模式</h3>
 * <pre>
 *   Long   -> LongTypeHandler
 *   String -> StringTypeHandler
 *   Date   -> DateTypeHandler
 *   自定义   -> 你自己写的 XxxTypeHandler（如 JSON 字段、枚举、加密字段）
 * </pre>
 * <b>实际项目最常用的扩展点之一</b>：想让实体里的 {@code List<Tag>} 自动存成 JSON 列，
 * 或者手机号入库自动加密，写一个 TypeHandler 就够了，业务代码零感知。
 *
 * @param <T> 该处理器负责的 Java 类型
 * @author mybatis-source-learn
 */
public interface TypeHandler<T> {

    /**
     * 【写】把 Java 值设置到 PreparedStatement 的第 i 个 ? 上。
     *
     * @param ps        预编译语句
     * @param i         参数下标（<b>从 1 开始</b>，JDBC 的经典坑）
     * @param parameter Java 侧的值，可能为 null
     * @param jdbcType  声明的 JDBC 类型，值为 null 时用它调 setNull
     */
    void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

    /**
     * 【读】按列名从结果集取值。
     */
    T getResult(ResultSet rs, String columnName) throws SQLException;

    /**
     * 【读】按列下标从结果集取值（下标同样从 1 开始）。
     */
    T getResult(ResultSet rs, int columnIndex) throws SQLException;

    /**
     * 【读】从存储过程的输出参数取值（本项目未做存储过程演示，保留接口完整性）。
     */
    default T getResult(CallableStatement cs, int columnIndex) throws SQLException {
        throw new UnsupportedOperationException("本简化版未支持存储过程输出参数");
    }
}
