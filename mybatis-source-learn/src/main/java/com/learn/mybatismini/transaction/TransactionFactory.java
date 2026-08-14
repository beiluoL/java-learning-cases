package com.learn.mybatismini.transaction;

import javax.sql.DataSource;

/**
 * 事务工厂（对应 MyBatis 的 {@code TransactionFactory}）。
 *
 * <p>由 {@code <transactionManager type="JDBC"/>} 配置决定用哪个实现。
 * SqlSessionFactory 每次 openSession 都会调它 new 一个新事务对象。
 *
 * @author mybatis-source-learn
 */
public interface TransactionFactory {

    /**
     * 创建事务。
     *
     * @param dataSource 数据源
     * @param autoCommit 是否自动提交
     */
    Transaction newTransaction(DataSource dataSource, boolean autoCommit);
}
