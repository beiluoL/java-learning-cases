package com.learn.mybatismini.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Boolean / boolean 的类型处理器。
 *
 * @author mybatis-source-learn
 */
public class BooleanTypeHandler extends BaseTypeHandler<Boolean> {

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setBoolean(i, parameter);
    }

    @Override
    protected Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getBoolean(columnName);
    }

    @Override
    protected Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getBoolean(columnIndex);
    }
}
