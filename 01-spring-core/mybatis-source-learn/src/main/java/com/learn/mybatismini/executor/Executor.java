package com.learn.mybatismini.executor;

import com.learn.mybatismini.cache.CacheKey;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * 执行器（对应 MyBatis 的 {@code Executor}）。
 *
 * <h3>SQL 执行的总指挥</h3>
 * SqlSession 的所有增删改查最终都落到 Executor：
 * <pre>
 *   query  查（含一级缓存 / 二级缓存判断）
 *   update 增删改（执行前清空一级缓存）
 *   commit / rollback  事务控制
 * </pre>
 *
 * <p>它是<b>模板方法 + 装饰器</b>的结合体：
 * <ul>
 *   <li>{@code BaseExecutor} 用模板方法抽出"缓存判断 / 事务 / 一级缓存"等公共骨架，
 *       把"真正的 JDBC 操作"留给 {@code doQuery / doUpdate} 让子类填；</li>
 *   <li>{@code CachingExecutor} 用装饰器包住一个 {@code BaseExecutor}，在不改动后者
 *       的前提下加上"二级缓存"能力；</li>
 *   <li>{@code SimpleExecutor} / {@code ReuseExecutor} 是两种 {@code doQuery} 策略。</li>
 * </ul>
 */
public interface Executor {

    /** 增 / 删 / 改 */
    int update(MappedStatement ms, Object parameter) throws SQLException;

    /** 查 */
    <E> List<E> query(MappedStatement ms, Object parameter) throws SQLException;

    /** 查（带缓存键，由 BaseExecutor 模板方法在内部调用） */
    <E> List<E> query(MappedStatement ms, Object parameter, BoundSql boundSql, CacheKey cacheKey) throws SQLException;

    void commit(boolean required) throws SQLException;

    void rollback(boolean required) throws SQLException;

    /** 取底层事务（即数据库连接持有者） */
    Transaction getTransaction();

    /** 清空一级缓存 */
    void clearLocalCache();

    /** 为一次查询生成缓存键（一级/二级缓存共用） */
    CacheKey createCacheKey(MappedStatement ms, Object parameter, BoundSql boundSql);

    void close(boolean forceRollback) throws SQLException;
}
