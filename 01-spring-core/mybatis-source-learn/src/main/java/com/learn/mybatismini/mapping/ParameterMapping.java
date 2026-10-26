package com.learn.mybatismini.mapping;

import com.learn.mybatismini.type.JdbcType;

/**
 * 参数映射：描述一个 {@code #{}} 占位符的元信息（对应 MyBatis 的 {@code ParameterMapping}）。
 *
 * <h3>它是怎么产生的？</h3>
 * SqlSourceBuilder 扫描 SQL 文本时，把每个 {@code #{xxx}} 替换成 {@code ?}，
 * 同时<b>按出现顺序</b>生成一个 ParameterMapping 放进 List。
 * <pre>
 *   原始：INSERT INTO t_user(user_name, age) VALUES(#{userName}, #{age})
 *   结果：INSERT INTO t_user(user_name, age) VALUES(?, ?)
 *        parameterMappings = [ (property=userName), (property=age) ]
 * </pre>
 *
 * <h3>为什么"顺序"至关重要？</h3>
 * 因为 JDBC 的 {@code ps.setXxx(i, value)} 是<b>按下标</b>定位占位符的。
 * DefaultParameterHandler 遍历这个 List，第 1 个映射对应第 1 个 {@code ?}。
 * 一旦顺序错乱，参数就会串位 —— 这也是为什么 SQL 文本和 mappings 必须一起生成、不可分离。
 *
 * <h3>支持的高级写法</h3>
 * <pre>
 *   #{age,jdbcType=INTEGER}                  显式指定 JDBC 类型（传 null 时必须）
 *   #{createTime,javaType=java.util.Date}    显式指定 Java 类型
 * </pre>
 *
 * @author mybatis-source-learn
 */
public class ParameterMapping {

    /** 属性表达式，如 userName、user.address.city、item（foreach 内） */
    private final String property;

    /** Java 类型（用于挑 TypeHandler）；解析阶段能推断出来就填，否则运行时按实际值推断 */
    private Class<?> javaType;

    /** JDBC 类型（主要用于 null 值的 setNull） */
    private JdbcType jdbcType;

    public ParameterMapping(String property) {
        this.property = property;
    }

    public ParameterMapping(String property, Class<?> javaType, JdbcType jdbcType) {
        this.property = property;
        this.javaType = javaType;
        this.jdbcType = jdbcType;
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

    @Override
    public String toString() {
        return "ParameterMapping{property='" + property + '\''
                + ", javaType=" + (javaType == null ? "?" : javaType.getSimpleName())
                + ", jdbcType=" + jdbcType + '}';
    }
}
