package com.learn.mybatismini.logging;

/**
 * 极简日志工具（替代 slf4j / log4j，避免引入第三方依赖）。
 *
 * <h3>它在学习中的作用</h3>
 * 真实 MyBatis 通过 {@code org.apache.ibatis.logging.Log} 适配各种日志框架，
 * 并在 {@code ConnectionLogger / PreparedStatementLogger} 里用 <b>JDK 动态代理</b>
 * 包装 JDBC 对象，从而打印出：
 * <pre>
 *   ==>  Preparing: SELECT * FROM t_user WHERE id = ?
 *   ==> Parameters: 1(Long)
 *   &lt;==      Total: 1
 * </pre>
 * 本项目为了让日志更直观，直接在关键节点（StatementHandler / ParameterHandler /
 * ResultSetHandler / 缓存 / 执行器）显式调用本类打印，格式刻意模仿 MyBatis。
 *
 * <p>日志级别很简单：只有 开/关（由 Configuration.logSqlEnabled 控制 SQL 日志）
 * 与 4 个语义化方法。所有输出走 System.out，便于在控制台观察执行链路。
 *
 * @author mybatis-source-learn
 */
public final class MiniLog {

    /** 全局开关：是否打印 SQL 相关日志（由 Configuration 在解析 settings 后设置） */
    private static volatile boolean sqlLogEnabled = true;

    /** 是否打印框架内部的"链路追踪"日志（Step10 生命周期演示会打开它） */
    private static volatile boolean traceEnabled = false;

    private MiniLog() {
    }

    public static void setSqlLogEnabled(boolean enabled) {
        sqlLogEnabled = enabled;
    }

    public static boolean isSqlLogEnabled() {
        return sqlLogEnabled;
    }

    public static void setTraceEnabled(boolean enabled) {
        traceEnabled = enabled;
    }

    // ------------------------------------------------------------------
    // SQL 三段式日志：Preparing / Parameters / Total
    // 与真实 MyBatis 的输出格式保持一致，方便你日后看真日志时秒懂
    // ------------------------------------------------------------------

    /** 打印即将预编译的 SQL（? 占位符形态） */
    public static void sqlPreparing(String sql) {
        if (sqlLogEnabled) {
            System.out.println("==>  Preparing: " + sql.replaceAll("\\s+", " ").trim());
        }
    }

    /** 打印实际绑定的参数，形如 {@code 1(Long), 张三(String)} */
    public static void sqlParameters(String params) {
        if (sqlLogEnabled) {
            System.out.println("==> Parameters: " + params);
        }
    }

    /** 打印查询返回的行数 */
    public static void sqlTotal(int total) {
        if (sqlLogEnabled) {
            System.out.println("<==      Total: " + total);
        }
    }

    /** 打印更新影响的行数 */
    public static void sqlUpdates(int rows) {
        if (sqlLogEnabled) {
            System.out.println("<==    Updates: " + rows);
        }
    }

    // ------------------------------------------------------------------
    // 语义化日志
    // ------------------------------------------------------------------

    /** 框架内部链路追踪（默认关闭，Step10 会打开） */
    public static void trace(String message) {
        if (traceEnabled) {
            System.out.println("   [trace] " + message);
        }
    }

    /** 缓存相关日志：命中/未命中/清空 */
    public static void cache(String message) {
        if (sqlLogEnabled) {
            System.out.println("   [cache] " + message);
        }
    }

    /** 普通信息 */
    public static void info(String message) {
        System.out.println("   [info ] " + message);
    }

    /** 警告 */
    public static void warn(String message) {
        System.out.println("   [warn ] " + message);
    }

    /** 错误（带异常） */
    public static void error(String message, Throwable t) {
        System.out.println("   [error] " + message + " -> " + t);
    }

    // ------------------------------------------------------------------
    // 演示排版辅助
    // ------------------------------------------------------------------

    /** 打印一级标题分隔条 */
    public static void title(String title) {
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("  " + title);
        System.out.println("================================================================================");
    }

    /** 打印二级小节标题 */
    public static void section(String section) {
        System.out.println();
        System.out.println("---- " + section + " " + "-".repeat(Math.max(0, 70 - section.length())));
    }
}
