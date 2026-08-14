package com.learn.mybatismini.executor.statement;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.executor.keygen.NoKeyGenerator;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.mapping.SqlCommandType;
import com.learn.mybatismini.session.Configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 预编译语句处理器（对应 MyBatis 的 {@code PreparedStatementHandler}，最常用）。
 *
 * <p>所有 {@code #{}} 占位符的 SQL 都走它：用 {@code PreparedStatement} 防 SQL 注入、
 * 预编译提升性能。本项目 99% 的语句都是它。
 */
public class PreparedStatementHandler extends BaseStatementHandler {

    public PreparedStatementHandler(Configuration configuration, Executor executor,
                                     MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        super(configuration, executor, mappedStatement, parameterObject, boundSql);
    }

    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        String sql = boundSql.getSql();
        MiniLog.sqlPreparing(sql);
        // 带自增主键的 INSERT/UPDATE：要求驱动返回生成的主键，供 Jdbc3KeyGenerator 回填
        boolean needGeneratedKeys = (mappedStatement.getSqlCommandType() == SqlCommandType.INSERT
                || mappedStatement.getSqlCommandType() == SqlCommandType.UPDATE)
                && !(mappedStatement.getKeyGenerator() instanceof NoKeyGenerator);
        if (needGeneratedKeys) {
            return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        }
        return connection.prepareStatement(sql);
    }

    @Override
    public void parameterize(Statement statement) throws SQLException {
        // 把 #{...} 的值设进 PreparedStatement
        parameterHandler.setParameters((PreparedStatement) statement);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> query(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        // ★ 关键：必须先把 SQL 真正执行掉，PreparedStatement 才会有结果集。
        //   之前漏了这一步，导致 getResultSet() 永远返回 null，所有查询都"查到 0 行"。
        ps.execute();
        return resultSetHandler.handleResultSets(ps);
    }

    @Override
    public int update(Statement statement) throws SQLException {
        PreparedStatement ps = (PreparedStatement) statement;
        int rows = ps.executeUpdate();
        MiniLog.sqlUpdates(rows);
        // ★ 自增主键回填：INSERT/UPDATE 成功后，把数据库生成的主键反射 set 回入参对象
        //   （对应 useGeneratedKeys="true" keyProperty="id" —— 这是 MyBatis 主键回填的全部秘密）
        mappedStatement.getKeyGenerator().processAfter(executor, mappedStatement, ps, parameterObject);
        return rows;
    }
}
