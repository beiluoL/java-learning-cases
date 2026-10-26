package com.learn.mybatismini.datasource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 数据源工厂接口（对应 MyBatis 的 {@code DataSourceFactory}）。
 *
 * <h3>为什么中间要加一层工厂？</h3>
 * XMLConfigBuilder 解析到
 * <pre>
 *   &lt;dataSource type="POOLED"&gt;
 *       &lt;property name="url" value="..."/&gt;
 *   &lt;/dataSource&gt;
 * </pre>
 * 时，它只拿到一个字符串 "POOLED" 和一堆 Properties。
 * 它不该知道 PooledDataSource 有哪些构造参数、哪些 setter。
 * 于是定义工厂接口：<b>"给你一堆属性，你还我一个 DataSource"</b>。
 *
 * <p>好处：想接入 Druid / HikariCP，只需写一个新的 DataSourceFactory 实现并注册别名，
 * 配置文件改个 type 就切换完成 —— 这是<b>工厂模式实现"可插拔"</b>的标准范式。
 *
 * @author mybatis-source-learn
 */
public interface DataSourceFactory {

    /**
     * 注入从 XML 解析出来的属性（driver / url / username / password / 池参数…）。
     */
    void setProperties(Properties props);

    /**
     * 产出数据源。
     */
    DataSource getDataSource();
}
