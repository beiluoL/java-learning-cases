package com.learn.mybatismini.executor;

import com.learn.mybatismini.executor.statement.StatementHandler;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.transaction.Transaction;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复用执行器（对应 MyBatis 的 {@code ReuseExecutor}）。
 *
 * <h3>与 SimpleExecutor 的唯一区别：缓存 Statement</h3>
 * 以 <b>SQL 文本</b> 为 key 把 {@code PreparedStatement} 缓存在 Map 里，
 * 同一条 SQL 第二次执行时直接复用、只重新设参数，省掉一次预编译。
 * 适合"同一条 SQL 被反复执行"的场景（如循环插入）。
 *
 * <p>所有缓存的 Statement 在 {@link #close} 时统一关闭。
 */
public class ReuseExecutor extends BaseExecutor {

    /** sql 文本 -> 已创建的 Statement（本 Executor 生命周期内复用） */
    private final Map<String, Statement> statementMap = new HashMap<>();

    public ReuseExecutor(Configuration configuration, Transaction transaction) {
        super(configuration, transaction);
    }

    @Override
    protected int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
        Configuration c = ms.getConfiguration();
        BoundSql boundSql = ms.getBoundSql(parameter);
        StatementHandler handler = c.newStatementHandler(this, ms, parameter, boundSql);
        Statement stmt = prepareStatement(handler, boundSql);
        try {
            return handler.update(stmt);
        } finally {
            // 复用型：不在此关闭 statement，由 close() 统一回收
        }
    }

    @Override
    protected <E> List<E> doQuery(MappedStatement ms, Object parameter, BoundSql boundSql) throws SQLException {
        Configuration c = ms.getConfiguration();
        StatementHandler handler = c.newStatementHandler(this, ms, parameter, boundSql);
        Statement stmt = prepareStatement(handler, boundSql);
        try {
            return handler.query(stmt);
        } finally {
            // 同 doUpdate
        }
    }

    private Statement prepareStatement(StatementHandler handler, BoundSql boundSql) throws SQLException {
        String sql = handler.getBoundSql().getSql();
        Statement stmt = statementMap.get(sql);
        if (stmt == null) {
            stmt = handler.prepare(transaction.getConnection());
            statementMap.put(sql, stmt);
        }
        handler.parameterize(stmt);
        return stmt;
    }

    @Override
    public void close(boolean forceRollback) throws SQLException {
        for (Statement stmt : statementMap.values()) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
                // 忽略
            }
        }
        statementMap.clear();
        super.close(forceRollback);
    }
}
