package com.learn.mybatismini.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * TypeHandler 抽象基类（对应 MyBatis 的 {@code BaseTypeHandler}）。
 *
 * <h3>模板方法模式（Template Method）的教科书示例</h3>
 * 所有类型处理器都有一段完全相同的公共逻辑：<b>处理 null</b>。
 * <pre>
 *   if (参数为 null) {
 *       ps.setNull(i, jdbcType);      // 公共处理，父类实现
 *   } else {
 *       setNonNullParameter(...);     // 各类型独有，抽象方法留给子类
 *   }
 * </pre>
 * 父类定义算法骨架（setParameter 是 final 的，不允许子类改流程），
 * 把变化的那一步（setNonNullParameter）延迟到子类实现。
 * 结果就是：写一个新的 TypeHandler 只需关心"非空时怎么读写"，样板代码全部省掉。
 *
 * <h3>为什么 null 要用 setNull(i, jdbcType) 而不是 setObject(i, null)？</h3>
 * 因为 JDBC 驱动需要知道这个 null 的"类型语义"。MySQL 宽容，Oracle 会直接抛
 * "ORA-17004: 无效的列类型"。所以 MyBatis 强制要求：<b>可能为 null 且无法推断类型的参数，
 * 必须显式写 jdbcType</b>，否则 fallback 到默认的 {@link JdbcType#OTHER}。
 *
 * @param <T> Java 类型
 * @author mybatis-source-learn
 */
public abstract class BaseTypeHandler<T> implements TypeHandler<T> {

    /**
     * 模板方法：统一处理 null，非 null 交给子类。
     * <p>声明为 final，防止子类破坏这个流程（模板方法模式的标准做法）。
     */
    @Override
    public final void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
            throws SQLException {
        if (parameter == null) {
            // jdbcType 没写时退化成 OTHER。多数驱动（含 MySQL/H2）能接受，Oracle 则必须显式指定
            JdbcType nullType = (jdbcType == null) ? JdbcType.OTHER : jdbcType;
            ps.setNull(i, nullType.getTypeCode());
        } else {
            setNonNullParameter(ps, i, parameter, jdbcType);
        }
    }

    /**
     * 模板方法：读取结果并统一处理"数据库 NULL"。
     * <p>注意 JDBC 的一个大坑：{@code rs.getInt()} 遇到 NULL 会返回 <b>0</b> 而不是 null！
     * 必须紧跟着调用 {@code rs.wasNull()} 判断刚才那次读取是否为 NULL。
     * 这也是为什么实体类的数值属性推荐用包装类型 Integer 而不是 int。
     */
    @Override
    public final T getResult(ResultSet rs, String columnName) throws SQLException {
        T result = getNullableResult(rs, columnName);
        return rs.wasNull() ? null : result;
    }

    @Override
    public final T getResult(ResultSet rs, int columnIndex) throws SQLException {
        T result = getNullableResult(rs, columnIndex);
        return rs.wasNull() ? null : result;
    }

    // ==================================================================
    // 留给子类实现的三个"变化点"
    // ==================================================================

    /** 非 null 时如何写入 PreparedStatement */
    protected abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
            throws SQLException;

    /** 按列名读取（可能返回 JDBC 的"零值"，由父类用 wasNull 校正） */
    protected abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

    /** 按列下标读取 */
    protected abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;
}
