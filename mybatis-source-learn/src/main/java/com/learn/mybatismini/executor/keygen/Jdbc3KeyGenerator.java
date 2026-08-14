package com.learn.mybatismini.executor.keygen;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.reflection.MetaObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库自增主键回填器（对应 MyBatis 的 {@code Jdbc3KeyGenerator}）。
 *
 * <h3>这就是 {@code insert useGeneratedKeys="true" keyProperty="id"} 的背后原理</h3>
 * <pre>
 *   1. 用 connection.prepareStatement(sql, RETURN_GENERATED_KEYS) 建语句
 *   2. 执行 INSERT
 *   3. 这里 processAfter 调用 statement.getGeneratedKeys() 拿到数据库生成的主键
 *   4. 用 MetaObject 把主键值反射 set 回入参对象的 keyProperty 属性
 *   —— 于是 insert 之后 user.getId() 不再是 null
 * </pre>
 *
 * <p>关键点：必须用 {@link MetaObject} 写值，因为 user 可能是任意 Bean，
 * 直接反射 setter 既繁琐又易错。这也是 MetaObject 在 MyBatis 里的第三大用武之地。
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

    @Override
    public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        // 自增场景无需事前处理
    }

    @Override
    public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
        if (parameter == null) {
            return;
        }
        String keyProperty = ms.getKeyProperty();
        if (keyProperty == null || keyProperty.isEmpty()) {
            return;
        }
        try (ResultSet rs = stmt.getGeneratedKeys()) {
            if (rs != null && rs.next()) {
                Object value = rs.getObject(1);
                MetaObject metaParam = ms.getConfiguration().newMetaObject(parameter);
                if (metaParam.hasSetter(keyProperty)) {
                    metaParam.setValue(keyProperty, value);
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("读取自增主键失败，keyProperty=" + keyProperty, e);
        }
    }
}
