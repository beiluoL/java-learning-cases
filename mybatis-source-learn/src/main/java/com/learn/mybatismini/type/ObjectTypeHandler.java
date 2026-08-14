package com.learn.mybatismini.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 兜底类型处理器：找不到更精确的处理器时使用。
 *
 * <p>它直接用 {@code setObject/getObject}，把类型判断交给 JDBC 驱动。
 * 优点是万能，缺点是失去了类型控制（尤其 null 场景），
 * 所以只作为 {@link TypeHandlerRegistry} 的最后防线。
 *
 * @author mybatis-source-learn
 */
public class ObjectTypeHandler extends BaseTypeHandler<Object> {

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter);
    }

    @Override
    protected Object getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName);
    }

    @Override
    protected Object getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex);
    }
}
