package com.learn.mybatismini.mapping;

import com.learn.mybatismini.type.JdbcType;

/**
 * 结果映射项：描述"某一列 -> 某个属性"的对应关系。
 * （对应 MyBatis 的 {@code ResultMapping}）
 *
 * <pre>
 *   &lt;result column="user_name" property="userName"/&gt;
 *          ↓
 *   ResultMapping{column="user_name", property="userName", flags=[]}
 *
 *   &lt;id column="id" property="id"/&gt;
 *          ↓
 *   ResultMapping{column="id", property="id", flags=[ID]}
 * </pre>
 *
 * @author mybatis-source-learn
 */
public class ResultMapping {

    /** 结果集列名 */
    private final String column;

    /** Java 属性名（支持 a.b.c 级联） */
    private final String property;

    /** 属性的 Java 类型（解析时由 MetaClass 推断） */
    private Class<?> javaType;

    /** 列的 JDBC 类型（可选） */
    private JdbcType jdbcType;

    /** 是否是主键列（<id> 标签） */
    private final boolean idFlag;

    public ResultMapping(String column, String property, boolean idFlag) {
        this.column = column;
        this.property = property;
        this.idFlag = idFlag;
    }

    public String getColumn() {
        return column;
    }

    public String getProperty() {
        return property;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public void setJavaType(Class<?> javaType) {
        this.javaType = javaType;
    }

    public JdbcType getJdbcType() {
        return jdbcType;
    }

    public void setJdbcType(JdbcType jdbcType) {
        this.jdbcType = jdbcType;
    }

    public boolean isIdFlag() {
        return idFlag;
    }

    @Override
    public String toString() {
        return "ResultMapping{" + column + " -> " + property + (idFlag ? " (ID)" : "") + '}';
    }
}
