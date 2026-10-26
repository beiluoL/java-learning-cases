package com.learn.mybatismini.executor.statement;

import com.learn.mybatismini.mapping.BoundSql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 语句处理器（对应 MyBatis 的 {@code StatementHandler}）。
 *
 * <h3>JDBC 的"最后一公里"</h3>
 * Executor 只负责"调度"，真正和 JDBC 打交道的是它：
 * <pre>
 *   prepare(Connection)    创建 Statement（PreparedStatement / Statement）
 *   parameterize(Statement) 把 #{...} 的值塞进 PreparedStatement（委托 ParameterHandler）
 *   query / update         执行并把结果交给 ResultSetHandler / 返回影响行数
 * </pre>
 * 这也是<b>策略模式</b>：{@code PreparedStatementHandler} 与 {@code SimpleStatementHandler}
 * 是两种"如何操作 Statement"的策略，由 MappedStatement 的 statementType 决定。
 */
public interface StatementHandler {

    Statement prepare(Connection connection) throws SQLException;

    void parameterize(Statement statement) throws SQLException;

    <E> List<E> query(Statement statement) throws SQLException;

    int update(Statement statement) throws SQLException;

    /** 取本次执行对应的 BoundSql */
    BoundSql getBoundSql();
}
