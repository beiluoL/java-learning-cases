package com.learn.mybatismini.type;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC 类型枚举（对应 MyBatis 的 {@code JdbcType}）。
 *
 * <h3>为什么要把 {@link java.sql.Types} 的 int 常量包成枚举？</h3>
 * {@code java.sql.Types} 里全是 {@code public static final int}，
 * 写 {@code setNull(i, 12)} 谁看得懂？包成枚举后可以：
 * <ul>
 *   <li>在 XML 里写 {@code jdbcType="VARCHAR"} 这种可读的字符串；</li>
 *   <li>编译期类型安全，不会传错随便一个 int；</li>
 *   <li>提供 {@link #forCode(int)} 反查，便于按 ResultSetMetaData 的类型码找回枚举。</li>
 * </ul>
 *
 * <h3>什么时候必须写 jdbcType？</h3>
 * 传 <b>null</b> 的时候！{@code ps.setObject(i, null)} 在 Oracle 等数据库上会报
 * "无效的列类型"，因为驱动不知道该按什么类型处理 null。
 * 此时必须 {@code ps.setNull(i, Types.VARCHAR)}。
 * 这就是 MyBatis 报 "JdbcType 未知" 那个经典错误的根源。
 *
 * @author mybatis-source-learn
 */
public enum JdbcType {

    NULL(Types.NULL),
    BOOLEAN(Types.BOOLEAN),
    TINYINT(Types.TINYINT),
    SMALLINT(Types.SMALLINT),
    INTEGER(Types.INTEGER),
    BIGINT(Types.BIGINT),
    FLOAT(Types.FLOAT),
    DOUBLE(Types.DOUBLE),
    DECIMAL(Types.DECIMAL),
    NUMERIC(Types.NUMERIC),
    CHAR(Types.CHAR),
    VARCHAR(Types.VARCHAR),
    LONGVARCHAR(Types.LONGVARCHAR),
    DATE(Types.DATE),
    TIME(Types.TIME),
    TIMESTAMP(Types.TIMESTAMP),
    BLOB(Types.BLOB),
    CLOB(Types.CLOB),
    OTHER(Types.OTHER);

    /** JDBC 类型码，即 java.sql.Types 中的常量值 */
    private final int typeCode;

    /** 类型码 -> 枚举 的反查表 */
    private static final Map<Integer, JdbcType> CODE_LOOKUP = new HashMap<>();

    static {
        for (JdbcType type : JdbcType.values()) {
            CODE_LOOKUP.put(type.typeCode, type);
        }
    }

    JdbcType(int code) {
        this.typeCode = code;
    }

    public int getTypeCode() {
        return typeCode;
    }

    /** 按 JDBC 类型码反查枚举，找不到返回 {@link #OTHER} */
    public static JdbcType forCode(int code) {
        return CODE_LOOKUP.getOrDefault(code, OTHER);
    }
}
