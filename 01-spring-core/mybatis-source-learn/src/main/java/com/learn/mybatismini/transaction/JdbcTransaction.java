package com.learn.mybatismini.transaction;

import com.learn.mybatismini.logging.MiniLog;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 基于原生 JDBC 的事务实现（对应 MyBatis 的 {@code JdbcTransaction}）。
 *
 * <h2>JDBC 事务三件套：autoCommit / commit / rollback</h2>
 * <pre>
 *   conn.setAutoCommit(false);   // 开启手动事务（这一步就是"begin"）
 *   ... 执行多条 SQL ...
 *   conn.commit();               // 全部生效
 *   conn.rollback();             // 全部撤销
 * </pre>
 * JDBC <b>没有</b>显式的 begin 方法，"关掉自动提交"就等于开启事务。
 * 这是很多初学者的盲区。
 *
 * <h3>autoCommit 与 SqlSession 的关系</h3>
 * <pre>
 *   sqlSessionFactory.openSession()        -> autoCommit=false，必须手动 commit
 *   sqlSessionFactory.openSession(true)    -> autoCommit=true，每条 SQL 自动提交
 * </pre>
 * <b>线上事故高发点</b>：用 {@code openSession()} 执行 insert 后忘记 commit，
 * 数据看起来插进去了（因为一级缓存/返回值都正常），但连接一关就全部回滚。
 *
 * <h3>延迟获取连接（Lazy Connection）</h3>
 * 构造时<b>不</b>取连接，只有真正执行 SQL 时才 {@link #getConnection()}。
 * 好处：{@code openSession()} 本身极轻量，即使只是拿个 SqlSession 什么都不做，
 * 也不会白占一条数据库连接。这个设计在真实 MyBatis 里同样存在。
 *
 * @author mybatis-source-learn
 */
public class JdbcTransaction implements Transaction {

    private final DataSource dataSource;

    /** 本次事务持有的连接（延迟创建，创建后固定不变） */
    private Connection connection;

    /** 是否自动提交 */
    private final boolean autoCommit;

    public JdbcTransaction(DataSource dataSource, boolean autoCommit) {
        this.dataSource = dataSource;
        this.autoCommit = autoCommit;
    }

    @Override
    public Connection getConnection() throws SQLException {
        // 延迟获取 + 缓存：保证整个事务用的是同一条连接
        if (connection == null) {
            openConnection();
        }
        return connection;
    }

    private void openConnection() throws SQLException {
        connection = dataSource.getConnection();
        if (connection.getAutoCommit() != autoCommit) {
            // ★ 这一句就是 JDBC 里的 "BEGIN TRANSACTION"
            connection.setAutoCommit(autoCommit);
        }
        MiniLog.trace("JdbcTransaction 开启事务，autoCommit=" + autoCommit);
    }

    @Override
    public void commit() throws SQLException {
        // autoCommit=true 时每条语句已自动提交，无需（也不能）再 commit
        if (connection != null && !connection.getAutoCommit()) {
            MiniLog.trace("JdbcTransaction 提交事务");
            connection.commit();
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (connection != null && !connection.getAutoCommit()) {
            MiniLog.trace("JdbcTransaction 回滚事务");
            connection.rollback();
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection != null) {
            // 关闭前把 autoCommit 复原。
            // 原因：连接可能来自连接池，会被下一个使用者借走，
            // 必须恢复成"出厂设置"避免污染。这个细节 MyBatis 源码里叫 resetAutoCommit。
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            MiniLog.trace("JdbcTransaction 关闭连接（归还连接池）");
            // 若是池化连接，这里的 close() 被动态代理拦截，实际是"归还"
            connection.close();
            connection = null;
        }
    }
}
