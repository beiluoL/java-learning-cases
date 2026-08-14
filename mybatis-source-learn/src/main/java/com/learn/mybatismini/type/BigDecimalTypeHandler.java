package com.learn.mybatismini.type;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * BigDecimal 的类型处理器（金额、精确小数的正确选择）。
 *
 * @author mybatis-source-learn
 */
public class BigDecimalTypeHandler extends BaseTypeHandler<BigDecimal> {

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i, BigDecimal parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setBigDecimal(i, parameter);
    }

    @Override
    protected BigDecimal getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getBigDecimal(columnName);
    }

    @Override
    protected BigDecimal getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getBigDecimal(columnIndex);
    }
}
