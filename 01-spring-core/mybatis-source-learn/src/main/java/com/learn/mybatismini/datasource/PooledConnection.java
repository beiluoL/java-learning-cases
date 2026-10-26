package com.learn.mybatismini.datasource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 池化连接的<b>动态代理</b>（对应 MyBatis 的 {@code PooledConnection}）。
 *
 * <h2>本类是全项目第一个 JDK 动态代理，务必吃透</h2>
 *
 * <h3>要解决的核心矛盾</h3>
 * 业务代码总是这样写：
 * <pre>
 *   try (Connection conn = dataSource.getConnection()) { ... }   // 自动 close()
 * </pre>
 * 但连接池<b>绝不希望连接被真的关掉</b>——关掉了还池化什么？
 * 可我们又不能要求所有人改成 {@code pool.giveBack(conn)}（那 JDBC 规范就白定了）。
 *
 * <h3>解法：偷换 close() 的语义</h3>
 * 用 JDK 动态代理生成一个"假的 Connection"交给业务方：
 * <pre>
 *   业务调 proxy.close()
 *        ↓ 被 invoke() 拦截
 *   发现方法名是 "close"  ->  不转发给真连接，而是 把连接还回池子
 *   其它方法（prepareStatement/commit/...） ->  原样转发给真连接
 * </pre>
 * 于是"关闭连接"这个动作被<b>偷偷改成了"归还连接"</b>，业务代码零改动。
 *
 * <h3>能这么做的前提</h3>
 * {@link Connection} 是<b>接口</b>。JDK 动态代理只能代理接口，
 * 而 JDBC 的核心类型（Connection / Statement / ResultSet）全是接口 ——
 * 这正是 MyBatis 能在 JDBC 各层无侵入插桩（日志、连接池、插件）的根本原因。
 *
 * <p>后面你会看到 MapperProxy 用同一套技术让"接口没有实现类也能执行 SQL"，
 * Plugin 用同一套技术实现拦截器链。<b>动态代理是 MyBatis 的灵魂。</b>
 *
 * @author mybatis-source-learn
 */
public class PooledConnection implements InvocationHandler {

    private static final String CLOSE = "close";

    /** 代理只需要 Connection 这一个接口 */
    private static final Class<?>[] IFACES = new Class<?>[]{Connection.class};

    /** 真实的物理连接 */
    private final Connection realConnection;

    /** 交给业务方使用的代理连接 */
    private final Connection proxyConnection;

    /** 所属连接池，close 时把自己还回去 */
    private final PooledDataSource dataSource;

    /** 连接是否仍然有效（归还后置为 false，防止业务方继续拿着旧代理乱用） */
    private boolean valid = true;

    /** 从池中取出的时间戳，用于统计连接被占用多久（可扩展为超时检测） */
    private long checkoutTimestamp;

    public PooledConnection(Connection realConnection, PooledDataSource dataSource) {
        this.realConnection = realConnection;
        this.dataSource = dataSource;
        this.checkoutTimestamp = System.currentTimeMillis();
        // ★ 生成代理对象：类加载器 + 接口数组 + 拦截处理器（就是 this）
        this.proxyConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), IFACES, this);
    }

    /**
     * 所有对代理连接的方法调用都会进到这里。
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // ---------- 拦截点 1：close() 改为"归还连接" ----------
        if (CLOSE.equals(methodName)) {
            dataSource.pushConnection(this);
            return null;
        }

        // ---------- 拦截点 2：已归还的连接不允许再使用 ----------
        // 注意排除 Object 的方法（toString/hashCode 等），否则调试打印时会误报
        if (!Object.class.equals(method.getDeclaringClass())) {
            checkConnection();
        }

        // ---------- 其它方法：原样转发给真实连接 ----------
        try {
            return method.invoke(realConnection, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 反射调用会把业务异常包一层，必须拆开重新抛出原始异常，
            // 否则上层 catch (SQLException) 会失效 —— 动态代理最常见的坑
            throw e.getTargetException();
        }
    }

    private void checkConnection() throws SQLException {
        if (!valid) {
            throw new SQLException("该连接已归还连接池，不能继续使用（请检查是否在 close() 之后仍持有引用）");
        }
    }

    // ------------------- 供连接池管理的方法 -------------------

    public Connection getProxyConnection() {
        return proxyConnection;
    }

    public Connection getRealConnection() {
        return realConnection;
    }

    public boolean isValid() {
        return valid && realConnection != null;
    }

    public void invalidate() {
        valid = false;
    }

    /** 重新启用（从空闲队列取出复用时调用） */
    public void revalidate() {
        valid = true;
        checkoutTimestamp = System.currentTimeMillis();
    }

    public long getCheckoutTime() {
        return System.currentTimeMillis() - checkoutTimestamp;
    }
}
