package com.learn.mybatismini.executor.statement;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.session.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 普通语句处理器（对应 MyBatis 的 {@code SimpleStatementHandler}）。
 *
 * <p>对应 {@code statementType="STATEMENT"}：用 {@code Statement} 而非 PreparedStatement。
 * 它的 SQL 里没有 {@code ?}（{@code ${}} 已内联成字面量），所以 parameterize 是空操作。
 * 本项目默认都用 PREPARED，这里保留它是为了演示"策略模式"的另一种实现。
 */
public class SimpleStatementHandler extends BaseStatementHandler {

    public SimpleStatementHandler(Configuration configuration, Executor executor,
                                  MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        super(configuration, executor, mappedStatement, parameterObject, boundSql);
    }

    @Override
    protected Statement instantiateStatement(Connection connection) throws SQLException {
        MiniLog.sqlPreparing(boundSql.getSql());
        return connection.createStatement();
    }

    @Override
    public void parameterize(Statement statement) throws SQLException {
        // 普通 Statement 无占位符，无需设参
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> query(Statement statement) throws SQLException {
        // 普通 Statement 的 SQL 里没有 ?（${} 已内联成字面量），直接用 execute 跑
        statement.execute(boundSql.getSql());
        return resultSetHandler.handleResultSets(statement);
    }

    @Override
    public int update(Statement statement) throws SQLException {
        int rows = statement.executeUpdate(boundSql.getSql());
        MiniLog.sqlUpdates(rows);
        return rows;
    }
}
