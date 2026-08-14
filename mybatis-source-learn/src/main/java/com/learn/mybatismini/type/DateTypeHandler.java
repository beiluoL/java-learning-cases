package com.learn.mybatismini.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * java.util.Date 的类型处理器。
 *
 * <h3>这里有个必须记住的类型陷阱</h3>
 * <pre>
 *   java.util.Date          业务代码用的（含日期 + 时间）
 *     ├── java.sql.Date       只有日期，时间部分被抹掉！
 *     ├── java.sql.Time       只有时间
 *     └── java.sql.Timestamp  日期 + 时间 + 纳秒  ← 与 util.Date 对应的应该是它
 * </pre>
 * 所以写入必须转成 {@link Timestamp}，读取也要用 {@code rs.getTimestamp()}。
 * 如果误用 {@code java.sql.Date}，时分秒会全部变成 00:00:00 —— 线上事故常见来源。
 *
 * @author mybatis-source-learn
 */
public class DateTypeHandler extends BaseTypeHandler<Date> {

    @Override
    protected void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
            throws SQLException {
        // util.Date -> sql.Timestamp，保留时间精度
        ps.setTimestamp(i, new Timestamp(parameter.getTime()));
    }

    @Override
    protected Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : new Date(ts.getTime());
    }

    @Override
    protected Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnIndex);
        return ts == null ? null : new Date(ts.getTime());
    }
}
