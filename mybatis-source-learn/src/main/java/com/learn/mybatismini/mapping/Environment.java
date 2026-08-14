package com.learn.mybatismini.mapping;

import com.learn.mybatismini.transaction.TransactionFactory;

import javax.sql.DataSource;

/**
 * 运行环境：一个"事务工厂 + 数据源"的组合。
 * （对应 MyBatis 的 {@code Environment}）
 *
 * <p>对应配置：
 * <pre>
 *   &lt;environment id="development"&gt;
 *       &lt;transactionManager type="JDBC"/&gt;
 *       &lt;dataSource type="POOLED"&gt;...&lt;/dataSource&gt;
 *   &lt;/environment&gt;
 * </pre>
 *
 * <p>MyBatis 允许配多套环境（dev/test/prod），通过 {@code <environments default="…">}
 * 指定生效的那一套；也可以在构建时用
 * {@code new SqlSessionFactoryBuilder().build(reader, "test")} 显式切换。
 *
 * <p>Configuration 只持有<b>一个</b>生效的 Environment ——
 * 换言之一个 SqlSessionFactory 只对应一个数据源。
 * 多数据源场景需要建多个 SqlSessionFactory，这也是分库分表中间件的切入点。
 *
 * @author mybatis-source-learn
 */
public class Environment {

    /** 环境 id，如 development */
    private final String id;

    /** 事务工厂 */
    private final TransactionFactory transactionFactory;

    /** 数据源 */
    private final DataSource dataSource;

    public Environment(String id, TransactionFactory transactionFactory, DataSource dataSource) {
        if (id == null) {
            throw new IllegalArgumentException("environment 的 id 不能为空");
        }
        if (transactionFactory == null) {
            throw new IllegalArgumentException("environment 必须配置 transactionManager");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("environment 必须配置 dataSource");
        }
        this.id = id;
        this.transactionFactory = transactionFactory;
        this.dataSource = dataSource;
    }

    public String getId() {
        return id;
    }

    public TransactionFactory getTransactionFactory() {
        return transactionFactory;
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
