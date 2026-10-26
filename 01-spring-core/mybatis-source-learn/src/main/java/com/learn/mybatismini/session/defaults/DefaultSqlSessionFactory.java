package com.learn.mybatismini.session.defaults;

import com.learn.mybatismini.mapping.Environment;
import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.ExecutorType;
import com.learn.mybatismini.session.SqlSession;
import com.learn.mybatismini.session.SqlSessionFactory;
import com.learn.mybatismini.transaction.Transaction;
import com.learn.mybatismini.transaction.TransactionFactory;

import java.sql.SQLException;

/**
 * 默认 SqlSessionFactory（对应 MyBatis 的 {@code DefaultSqlSessionFactory}）。
 *
 * <h3>openSession 做了什么？</h3>
 * <pre>
 *   Environment      从 Configuration 取出"事务工厂 + 数据源"
 *        │
 *   Transaction       newTransaction(dataSource, autoCommit)  —— 延迟取连接
 *        │
 *   Executor          configuration.newExecutor(tx, 类型)      —— Simple/Reuse + 套 Caching + 套插件
 *        │
 *   SqlSession       new DefaultSqlSession(config, executor)  —— 门面
 * </pre>
 * 每次 openSession 都是"新连接 + 新 Executor"，所以 SqlSession <b>不是</b>线程安全的，
 * 但 SqlSessionFactory 是。
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

    private final Configuration configuration;

    public DefaultSqlSessionFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public SqlSession openSession() {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), false);
    }

    /** 以指定事务方式开启会话（供测试/特殊场景） */
    public SqlSession openSession(ExecutorType type) {
        return openSessionFromDataSource(type, false);
    }

    public SqlSession openSession(boolean autoCommit) {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), autoCommit);
    }

    private SqlSession openSessionFromDataSource(ExecutorType type, boolean autoCommit) {
        Transaction tx = null;
        try {
            Environment env = configuration.getEnvironment();
            TransactionFactory tf = env.getTransactionFactory();
            tx = tf.newTransaction(env.getDataSource(), autoCommit);
            Executor executor = configuration.newExecutor(tx, type);
            return new DefaultSqlSession(configuration, executor, autoCommit);
        } catch (Exception e) {
            if (tx != null) {
                try {
                    tx.close();
                } catch (SQLException ignored) {
                    // 忽略关闭异常
                }
            }
            throw new PersistenceException("创建 SqlSession 失败", e);
        }
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}
