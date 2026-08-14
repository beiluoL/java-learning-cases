package com.learn.mybatismini.session;

/**
 * 执行器类型（对应 MyBatis 的 {@code ExecutorType}）。
 *
 * <p>本简化版实现了 {@code SIMPLE} 与 {@code REUSE} 两种，与全局配置
 * {@code defaultExecutorType} 对应。MyBatis 还有 {@code BATCH}（批量），略去。
 *
 * <ul>
 *   <li>{@code SIMPLE}：每条 SQL 都新建一个 {@code Statement}，用完即关（默认）；</li>
 *   <li>{@code REUSE}：缓存 {@code Statement}，相同 SQL 复用同一个，减少预编译开销。</li>
 * </ul>
 */
public enum ExecutorType {
    SIMPLE,
    REUSE
}
