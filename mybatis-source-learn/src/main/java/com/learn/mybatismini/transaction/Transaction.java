package com.learn.mybatismini.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 事务抽象（对应 MyBatis 的 {@code Transaction}）。
 *
 * <h3>为什么要把事务抽象成接口？</h3>
 * 因为"谁来 commit"在不同环境下答案不同：
 * <pre>
 *   独立使用 MyBatis        -> 自己管：JdbcTransaction 直接调 connection.commit()
 *   跑在 Spring 里          -> 交给 Spring 的事务管理器（SpringManagedTransaction）
 *   跑在 JavaEE 容器/JTA 里  -> 交给容器（ManagedTransaction，commit 方法是空实现！）
 * </pre>
 * 上层的 {@code SqlSession.commit()} 只管调 {@code transaction.commit()}，
 * 具体行为由实现类决定。这就是"<b>面向接口编程 + 策略模式</b>"带来的可移植性。
 *
 * <p>顺带解答一个常见困惑：<b>为什么 MyBatis 整合 Spring 后不用手动 commit？</b>
 * 因为 mybatis-spring 换掉了 TransactionFactory 的实现，
 * SqlSession.commit() 被路由到 Spring 的事务同步机制上，由 @Transactional 统一收口。
 *
 * @author mybatis-source-learn
 */
public interface Transaction {

    /**
     * 获取本次事务使用的数据库连接。
     * <p>注意：<b>一个事务内必须始终是同一条连接</b>，否则事务毫无意义
     * （不同连接就是不同的数据库会话，各自独立提交）。
     * 所以实现类会把连接缓存起来，第一次才真正 getConnection。
     */
    Connection getConnection() throws SQLException;

    /** 提交事务 */
    void commit() throws SQLException;

    /** 回滚事务 */
    void rollback() throws SQLException;

    /** 关闭连接（归还连接池） */
    void close() throws SQLException;
}
