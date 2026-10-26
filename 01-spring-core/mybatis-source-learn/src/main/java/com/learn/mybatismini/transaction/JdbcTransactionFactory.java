package com.learn.mybatismini.transaction;

import javax.sql.DataSource;

/**
 * {@link JdbcTransaction} 的工厂，对应配置 {@code <transactionManager type="JDBC"/>}。
 *
 * @author mybatis-source-learn
 */
public class JdbcTransactionFactory implements TransactionFactory {

    @Override
    public Transaction newTransaction(DataSource dataSource, boolean autoCommit) {
        return new JdbcTransaction(dataSource, autoCommit);
    }
}
