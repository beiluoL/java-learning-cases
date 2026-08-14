package com.learn.mybatismini.datasource;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.logging.MiniLog;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 无池化数据源：每次 {@link #getConnection()} 都建立一条<b>新的物理连接</b>。
 * （对应 MyBatis 的 {@code UnpooledDataSource}）
 *
 * <h3>为什么先实现这个"最笨"的版本？</h3>
 * 因为它最能说明"数据库连接到底是什么"：
 * <pre>
 *   DriverManager.getConnection(url, user, pwd)
 *     -> 驱动发起 TCP 三次握手
 *     -> 数据库做身份认证、分配会话（session）、分配内存
 *     -> 返回 Connection
 * </pre>
 * 整个过程通常要 <b>几十到上百毫秒</b>，而一次简单查询可能只要 1ms。
 * 连接创建的成本远高于 SQL 执行本身 —— 这就是必须有连接池的根本原因，
 * 也是 {@link PooledDataSource} 存在的意义。
 *
 * <h3>它的正当用途</h3>
 * 数据库迁移脚本、一次性初始化、单元测试 —— 这些场景连接用完即弃，无需池化。
 *
 * @author mybatis-source-learn
 */
public class UnpooledDataSource implements DataSource {

    private String driver;
    private String url;
    private String username;
    private String password;

    /** 是否自动提交（null 表示用驱动默认值） */
    private Boolean autoCommit;

    /** 记录一共创建了多少条物理连接，Demo 里用来对比连接池效果 */
    private int createdConnections = 0;

    public UnpooledDataSource() {
    }

    public UnpooledDataSource(String driver, String url, String username, String password) {
        this.driver = driver;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return doGetConnection(username, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return doGetConnection(username, password);
    }

    private synchronized Connection doGetConnection(String user, String pwd) throws SQLException {
        // 第一步：加载并注册驱动。
        // JDBC 4.0 起驱动可通过 SPI（META-INF/services/java.sql.Driver）自动注册，
        // 但显式 Class.forName 更保险（老驱动/特殊类加载器场景）。
        initializeDriver();

        Properties props = new Properties();
        if (user != null) {
            props.setProperty("user", user);
        }
        if (pwd != null) {
            props.setProperty("password", pwd);
        }

        // 第二步：真正建立物理连接（昂贵操作）
        Connection connection = DriverManager.getConnection(url, props);
        createdConnections++;
        MiniLog.trace("UnpooledDataSource 新建物理连接 #" + createdConnections
                + " -> " + connection.getClass().getSimpleName());

        if (autoCommit != null && connection.getAutoCommit() != autoCommit) {
            connection.setAutoCommit(autoCommit);
        }
        return connection;
    }

    private void initializeDriver() {
        if (driver == null) {
            return;
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new PersistenceException("加载 JDBC 驱动失败: " + driver
                    + "（请确认对应的驱动 jar 已在 classpath 上）", e);
        }
    }

    public int getCreatedConnections() {
        return createdConnections;
    }

    // ------------------- getter / setter -------------------

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setAutoCommit(Boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDriver() {
        return driver;
    }

    // ------------------- DataSource 接口的其余方法（本项目不需要） -------------------

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // 不支持
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // 不支持
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException(getClass().getName() + " 不是包装器");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    @Override
    public java.util.logging.Logger getParentLogger() {
        return java.util.logging.Logger.getLogger(getClass().getName());
    }
}
