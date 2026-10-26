package com.learn.mybatismini.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * String 的类型处理器。
 *
 * <p>它是"防 SQL 注入"的实际执行者：{@code ps.setString()} 会由 JDBC 驱动
 * 对引号等特殊字符做转义/走二进制协议，因此 {@code #{}} 天然安全，
 * 而 {@code ${}} 直接拼字符串就绕过了这一层保护。
 *
 * @author mybatis-source-learn
 */
public class StringTypeHandler extends BaseTypeHandler<String> {

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    protected String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    protected String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }
}
