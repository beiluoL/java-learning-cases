package com.learn.mybatismini.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Double / double 的类型处理器。
 *
 * <p>提醒：金额字段<b>不要</b>用 Double（二进制浮点有精度损失），
 * 应当用 {@link java.math.BigDecimal} 配 {@link BigDecimalTypeHandler}。
 *
 * @author mybatis-source-learn
 */
public class DoubleTypeHandler extends BaseTypeHandler<Double> {

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i, Double parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setDouble(i, parameter);
    }

    @Override
    protected Double getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getDouble(columnName);
    }

    @Override
    protected Double getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getDouble(columnIndex);
    }
}
