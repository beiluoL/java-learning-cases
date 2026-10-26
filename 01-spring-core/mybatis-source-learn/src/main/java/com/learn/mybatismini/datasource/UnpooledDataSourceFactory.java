package com.learn.mybatismini.datasource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * {@link UnpooledDataSource} 的工厂，对应配置 {@code <dataSource type="UNPOOLED">}。
 *
 * @author mybatis-source-learn
 */
public class UnpooledDataSourceFactory implements DataSourceFactory {

    protected UnpooledDataSource dataSource;

    public UnpooledDataSourceFactory() {
        this.dataSource = new UnpooledDataSource();
    }

    @Override
    public void setProperties(Properties props) {
        applyCommonProperties(dataSource, props);
    }

    /**
     * 把 Properties 里的通用连接参数注入数据源。
     * <p>抽成 protected 方法，供子类 {@link PooledDataSourceFactory} 复用。
     */
    protected void applyCommonProperties(UnpooledDataSource ds, Properties props) {
        ds.setDriver(props.getProperty("driver"));
        ds.setUrl(props.getProperty("url"));
        ds.setUsername(props.getProperty("username"));
        ds.setPassword(props.getProperty("password"));
        String autoCommit = props.getProperty("autoCommit");
        if (autoCommit != null) {
            ds.setAutoCommit(Boolean.valueOf(autoCommit));
        }
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }
}
