package com.learn.mybatismini.executor.statement;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 结果集处理器（对应 MyBatis 的 {@code ResultSetHandler}）。
 *
 * <p>把 JDBC 的 {@code ResultSet} 变成 Java 对象 / 对象列表。最难、也最能体现 MyBatis 功力的一环。
 */
public interface ResultSetHandler {

    /**
     * 处理一个 Statement 的结果集，返回映射后的对象列表。
     */
    <E> List<E> handleResultSets(Statement stmt) throws SQLException;
}
