package com.learn.mybatismini.executor.keygen;

import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.mapping.MappedStatement;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * 主键生成器接口（对应 MyBatis 的 {@code KeyGenerator}）。
 *
 * <h3>两大时机</h3>
 * <pre>
 *   processBefore  —— 语句执行"前"：用于 selectKey 那种"先查主键再插入"的场景
 *   processAfter   —— 语句执行"后"：用于数据库自增主键回填（Jdbc3KeyGenerator）
 * </pre>
 *
 * <p>本简化版只实现自增回填（{@code processAfter}），
 * {@code processBefore} 留空。{@code NoKeyGenerator} 两者都空，表示"不管主键"。
 */
public interface KeyGenerator {

    /**
     * 语句执行前回调。
     */
    void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) throws SQLException;

    /**
     * 语句执行后回调，此时可以从 {@code stmt.getGeneratedKeys()} 拿到自增主键。
     */
    void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) throws SQLException;
}
