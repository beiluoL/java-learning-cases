package com.learn.mybatismini.datasource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * {@link PooledDataSource} 的工厂，对应配置 {@code <dataSource type="POOLED">}。
 *
 * <p>它继承 {@link UnpooledDataSourceFactory} 复用"通用连接参数注入"逻辑，
 * 只额外解析连接池特有的参数。
 *
 * @author mybatis-source-learn
 */
public class PooledDataSourceFactory extends UnpooledDataSourceFactory {

    private final PooledDataSource pooledDataSource;

    public PooledDataSourceFactory() {
        // 池化数据源内部持有一个 UnpooledDataSource 负责建物理连接（组合）
        this.pooledDataSource = new PooledDataSource();
    }

    @Override
    public void setProperties(Properties props) {
        // 1. 注入通用连接参数到底层的 UnpooledDataSource
        applyCommonProperties(pooledDataSource.getUnderlyingDataSource(), props);

        // 2. 注入连接池参数
        String maxActive = props.getProperty("poolMaximumActiveConnections");
        if (maxActive != null) {
            pooledDataSource.setPoolMaximumActiveConnections(Integer.parseInt(maxActive));
        }
        String maxIdle = props.getProperty("poolMaximumIdleConnections");
        if (maxIdle != null) {
            pooledDataSource.setPoolMaximumIdleConnections(Integer.parseInt(maxIdle));
        }
        String maxWait = props.getProperty("poolMaximumWaitTime");
        if (maxWait != null) {
            pooledDataSource.setPoolMaximumWaitTime(Integer.parseInt(maxWait));
        }
    }

    @Override
    public DataSource getDataSource() {
        return pooledDataSource;
    }
}
