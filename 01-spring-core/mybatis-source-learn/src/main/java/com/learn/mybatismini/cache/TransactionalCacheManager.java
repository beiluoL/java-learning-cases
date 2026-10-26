package com.learn.mybatismini.cache;

import com.learn.mybatismini.cache.decorators.TransactionalCache;

import java.util.HashMap;
import java.util.Map;

/**
 * 事务缓存管理器（对应 MyBatis 的 {@code TransactionalCacheManager}）。
 *
 * <h3>它管什么？</h3>
 * 一个 SqlSession 可能操作多个 Mapper（多个 namespace），
 * 每个 namespace 有自己的二级缓存。因此需要：
 * <pre>
 *   Map&lt;真实二级缓存, 对应的 TransactionalCache 包装&gt;
 * </pre>
 * 当 SqlSession.commit() 时，逐个调用它们的 commit()，把各自暂存区刷进真缓存。
 *
 * <p>它由 {@code CachingExecutor} 持有，生命周期与 SqlSession 相同。
 * 换句话说：<b>暂存区是"会话私有"的，别的会话看不到你未提交的写入</b>，
 * 这就是二级缓存的事务隔离实现。
 *
 * @author mybatis-source-learn
 */
public class TransactionalCacheManager {

    /** 真实缓存 -> 事务包装缓存 */
    private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

    /** 清空（标记 clearOnCommit） */
    public void clear(Cache cache) {
        getTransactionalCache(cache).clear();
    }

    public Object getObject(Cache cache, CacheKey key) {
        return getTransactionalCache(cache).getObject(key);
    }

    public void putObject(Cache cache, CacheKey key, Object value) {
        getTransactionalCache(cache).putObject(key, value);
    }

    /** SqlSession.commit() 时调用：所有 namespace 的暂存区统一过账 */
    public void commit() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.commit();
        }
    }

    /** SqlSession.rollback() 时调用：所有暂存区统一丢弃 */
    public void rollback() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.rollback();
        }
    }

    /** 懒加载：第一次操作某 namespace 的缓存时才创建其事务包装 */
    private TransactionalCache getTransactionalCache(Cache cache) {
        return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
    }
}
