package com.finpay.business.infrastructure.handler;

import com.finpay.security.crypto.SensitiveCryptoService;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 敏感字段类型处理器。标注了本处理器的 String 字段在写入数据库前自动 AES 加密，
 * 读取后自动解密，密钥由安全模块 {@link SensitiveCryptoService} 提供。
 *
 * <p>使用方式：在实体字段上 {@code @TableField(typeHandler = SensitiveTypeHandler.class)}。</p>
 */
@Component
public class SensitiveTypeHandler extends BaseTypeHandler<String> implements ApplicationContextAware {

    private static ApplicationContext CTX;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        CTX = applicationContext;
    }

    private SensitiveCryptoService service() {
        return CTX.getBean(SensitiveCryptoService.class);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, service().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String v = rs.getString(columnName);
        return v == null ? null : service().decrypt(v);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String v = rs.getString(columnIndex);
        return v == null ? null : service().decrypt(v);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String v = cs.getString(columnIndex);
        return v == null ? null : service().decrypt(v);
    }
}
