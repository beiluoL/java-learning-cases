package com.learn.mybatismini.datasource;

import com.learn.mybatismini.logging.MiniLog;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 简易连接池数据源（对应 MyBatis 的 {@code PooledDataSource}，做了大幅精简）。
 *
 * <h2>连接池的本质：用"借还"替代"创建销毁"</h2>
 * <pre>
 *   空闲队列 idleConnections  ：造好但没人用的连接，等着被借走
 *   活跃列表 activeConnections：已经借出去、正在被使用的连接
 *
 *   借（popConnection）：
 *     空闲队列有货     -> 直接拿一个（几乎零成本，这就是池化的收益）
 *     没货但没到上限   -> 新建物理连接
 *     没货且已达上限   -> wait() 阻塞等别人归还
 *
 *   还（pushConnection）：
 *     空闲数没超 maxIdle -> rollback 清理状态后放回空闲队列，notifyAll 唤醒等待者
 *     空闲数已超         -> 真正 close() 掉物理连接
 * </pre>
 *
 * <h3>三个容易被忽略但极其重要的细节</h3>
 * <ol>
 *   <li><b>归还前必须 rollback()</b>：如果业务开了事务却没提交就 close，
 *       这条连接带着未提交事务回到池里，下一个借到它的人会莫名其妙地继承脏事务。
 *       所以归还时强制回滚，把连接恢复成"干净"状态。</li>
 *   <li><b>归还后要 invalidate 旧代理</b>：同一条物理连接会被包成新的 PooledConnection
 *       再借出去。旧代理必须失效，否则前一个使用者还能操作已经借给别人的连接。</li>
 *   <li><b>wait/notifyAll 必须在 synchronized 块内</b>，且 wait 要放在 while 循环里
 *       防止虚假唤醒 —— 这是 Java 并发的基本功。</li>
 * </ol>
 *
 * @author mybatis-source-learn
 */
public class PooledDataSource implements DataSource {

    /** 底层真正负责建立物理连接的数据源（组合复用，不是继承） */
    private final UnpooledDataSource dataSource;

    /** 最大活跃连接数 */
    private int poolMaximumActiveConnections = 10;

    /** 最大空闲连接数 */
    private int poolMaximumIdleConnections = 5;

    /** 取连接的最长等待时间（毫秒） */
    private int poolMaximumWaitTime = 20_000;

    /** 空闲连接队列 */
    private final List<PooledConnection> idleConnections = new ArrayList<>();

    /** 活跃连接列表 */
    private final List<PooledConnection> activeConnections = new ArrayList<>();

    /** 统计：累计借出次数 / 累计真实新建连接数（Demo 里用来直观展示复用率） */
    private int requestCount = 0;
    private int createdConnectionCount = 0;

    public PooledDataSource() {
        this.dataSource = new UnpooledDataSource();
    }

    public PooledDataSource(UnpooledDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        // ★ 注意返回的是代理连接，不是物理连接
        return popConnection().getProxyConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    /**
     * 借连接。
     */
    private PooledConnection popConnection() throws SQLException {
        long startTime = System.currentTimeMillis();
        synchronized (this) {
            requestCount++;
            while (true) {
                // 情况一：有空闲连接，直接复用（最快路径）
                if (!idleConnections.isEmpty()) {
                    PooledConnection conn = idleConnections.remove(idleConnections.size() - 1);
                    conn.revalidate();
                    activeConnections.add(conn);
                    MiniLog.trace("连接池：复用空闲连接（空闲=" + idleConnections.size()
                            + ", 活跃=" + activeConnections.size() + "）");
                    return conn;
                }

                // 情况二：无空闲但未达上限，新建
                if (activeConnections.size() < poolMaximumActiveConnections) {
                    PooledConnection conn = new PooledConnection(dataSource.getConnection(), this);
                    createdConnectionCount++;
                    activeConnections.add(conn);
                    MiniLog.trace("连接池：新建物理连接（累计新建=" + createdConnectionCount
                            + ", 活跃=" + activeConnections.size() + "）");
                    return conn;
                }

                // 情况三：已达上限，等待别人归还
                long waited = System.currentTimeMillis() - startTime;
                if (waited >= poolMaximumWaitTime) {
                    throw new SQLException("获取连接超时（已等待 " + waited + "ms）。"
                            + "可能是连接泄漏：某处拿了连接却没有 close()。");
                }
                try {
                    MiniLog.trace("连接池：已达上限 " + poolMaximumActiveConnections + "，等待归还…");
                    // wait 必须在 while 循环里，避免虚假唤醒
                    wait(poolMaximumWaitTime - waited);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("等待连接时被中断", e);
                }
            }
        }
    }

    /**
     * 还连接。由 {@link PooledConnection#invoke} 拦截到 close() 时调用。
     */
    protected void pushConnection(PooledConnection conn) throws SQLException {
        synchronized (this) {
            activeConnections.remove(conn);

            if (!conn.isValid()) {
                MiniLog.trace("连接池：丢弃一个失效连接");
                return;
            }

            if (idleConnections.size() < poolMaximumIdleConnections) {
                // ★ 关键：回滚未提交事务，把连接恢复成干净状态
                Connection real = conn.getRealConnection();
                if (!real.getAutoCommit()) {
                    real.rollback();
                }
                // 用同一条物理连接重新包一个代理，旧代理作废
                PooledConnection newConn = new PooledConnection(real, this);
                idleConnections.add(newConn);
                conn.invalidate();
                MiniLog.trace("连接池：连接归还（空闲=" + idleConnections.size()
                        + ", 活跃=" + activeConnections.size() + "）");
                // 唤醒所有在 popConnection 里等待的线程
                notifyAll();
            } else {
                // 空闲太多，真正关闭物理连接
                Connection real = conn.getRealConnection();
                if (!real.getAutoCommit()) {
                    real.rollback();
                }
                real.close();
                conn.invalidate();
                MiniLog.trace("连接池：空闲已满，物理关闭一条连接");
            }
        }
    }

    /** 关闭池中所有连接（应用退出时调用） */
    public synchronized void forceCloseAll() {
        for (PooledConnection conn : new ArrayList<>(activeConnections)) {
            closeQuietly(conn);
        }
        activeConnections.clear();
        for (PooledConnection conn : new ArrayList<>(idleConnections)) {
            closeQuietly(conn);
        }
        idleConnections.clear();
        MiniLog.trace("连接池：已强制关闭所有连接");
    }

    private void closeQuietly(PooledConnection conn) {
        try {
            conn.invalidate();
            Connection real = conn.getRealConnection();
            if (real != null && !real.isClosed()) {
                if (!real.getAutoCommit()) {
                    real.rollback();
                }
                real.close();
            }
        } catch (SQLException ignored) {
            // 关闭阶段的异常忽略即可
        }
    }

    // ------------------- 统计信息（Demo 展示用） -------------------

    public synchronized int getIdleConnectionCount() {
        return idleConnections.size();
    }

    public synchronized int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public synchronized int getRequestCount() {
        return requestCount;
    }

    public synchronized int getCreatedConnectionCount() {
        return createdConnectionCount;
    }

    /** 借出次数 vs 真实新建数 的对比字符串 */
    public synchronized String stats() {
        return "借出 " + requestCount + " 次，实际只建了 " + createdConnectionCount
                + " 条物理连接（复用 " + (requestCount - createdConnectionCount) + " 次）";
    }

    // ------------------- 配置项 -------------------

    public UnpooledDataSource getUnderlyingDataSource() {
        return dataSource;
    }

    public void setPoolMaximumActiveConnections(int v) {
        this.poolMaximumActiveConnections = v;
    }

    public void setPoolMaximumIdleConnections(int v) {
        this.poolMaximumIdleConnections = v;
    }

    public void setPoolMaximumWaitTime(int v) {
        this.poolMaximumWaitTime = v;
    }

    // ------------------- DataSource 其余方法 -------------------

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
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
