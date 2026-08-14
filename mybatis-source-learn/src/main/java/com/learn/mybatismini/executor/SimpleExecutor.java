package com.learn.mybatismini.executor;

import com.learn.mybatismini.executor.statement.StatementHandler;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.transaction.Transaction;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 简单执行器（对应 MyBatis 的 {@code SimpleExecutor}，默认执行器）。
 *
 * <p>最朴素的做法：每次查询/更新都新建一个 {@code Statement}，用完即关。
 * 代码最简单、最容易读懂，也是理解执行流程的最佳入口。
 */
public class SimpleExecutor extends BaseExecutor {

    public SimpleExecutor(Configuration configuration, Transaction transaction) {
        super(configuration, transaction);
    }

    @Override
    protected int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
        Configuration c = ms.getConfiguration();
        StatementHandler handler = c.newStatementHandler(this, ms, parameter, ms.getBoundSql(parameter));
        Statement stmt = prepareStatement(handler);
        try {
            return handler.update(stmt);
        } finally {
            closeStatement(stmt);
        }
    }

    @Override
    protected <E> List<E> doQuery(MappedStatement ms, Object parameter, BoundSql boundSql) throws SQLException {
        Configuration c = ms.getConfiguration();
        StatementHandler handler = c.newStatementHandler(this, ms, parameter, boundSql);
        Statement stmt = prepareStatement(handler);
        try {
            return handler.query(stmt);
        } finally {
            closeStatement(stmt);
        }
    }

    /** 建连接 -> 建 Statement -> 设参数（模板，Simple/Reuse 共用） */
    private Statement prepareStatement(StatementHandler handler) throws SQLException {
        Statement stmt = handler.prepare(transaction.getConnection());
        handler.parameterize(stmt);
        return stmt;
    }

    static void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
                // 关闭失败不阻断主流程
            }
        }
    }
}
