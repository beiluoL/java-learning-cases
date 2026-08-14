package com.learn.mybatismini.executor;

import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.cache.CacheKey;
import com.learn.mybatismini.cache.TransactionalCacheManager;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.transaction.Transaction;

import java.sql.SQLException;
import java.util.List;

/**
 * 二级缓存执行器（对应 MyBatis 的 {@code CachingExecutor}）。
 *
 * <h3>装饰器：在"真正的执行器"外面套一层二级缓存</h3>
 * <pre>
 *   CachingExecutor(SimpleExecutor)
 *        │  query：先查 namespace 二级缓存
 *        ├─ 命中 -> 直接返回
 *        └─ 未命中 -> 委托 SimpleExecutor（其内部再走一级缓存/查库）
 * </pre>
 *
 * <h3>为什么需要 TransactionalCacheManager？</h3>
 * 二级缓存是<b>跨 SqlSession</b> 共享的，但 MyBatis 规定事务提交前不能让别的会话看到
 * 你刚查的数据（隔离性）。所以查到的数据先放进"暂存区"，等 {@code commit} 才真正刷入
 * 共享缓存；{@code rollback} 则丢弃。这就是 {@code TransactionalCache} 的作用。
 */
public class CachingExecutor implements Executor {

    /** 被装饰的真实执行器（Simple/Reuse） */
    private final Executor delegate;

    /** 管理各 namespace 的事务缓存 */
    private final TransactionalCacheManager tcm = new TransactionalCacheManager();

    public CachingExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        // 写操作：若需要，清空该 namespace 的二级缓存（防止读到旧数据）
        flushCacheIfRequired(ms);
        return delegate.update(ms, parameter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> query(MappedStatement ms, Object parameter) throws SQLException {
        Cache cache = ms.getCache();
        if (cache != null && ms.isUseCache()) {
            BoundSql boundSql = ms.getBoundSql(parameter);
            CacheKey key = delegate.createCacheKey(ms, parameter, boundSql);
            // 1) 查二级缓存（暂存区 / 已提交区）
            List<E> list = (List<E>) tcm.getObject(cache, key);
            if (list == null) {
                MiniLog.cache("未命中二级缓存，查询数据库 -> " + ms.getId());
                // 2) 未命中 -> 委托底层执行器（其内部走一级缓存/查库）
                list = delegate.query(ms, parameter, boundSql, key);
                // 3) 放进暂存区，等 commit 才真正共享
                tcm.putObject(cache, key, list);
            } else {
                MiniLog.cache("命中二级缓存，直接返回（跨会话共享、未查库） -> " + ms.getId());
            }
            return list;
        }
        // 该语句不走二级缓存：直接委托
        return delegate.query(ms, parameter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> query(MappedStatement ms, Object parameter, BoundSql boundSql, CacheKey cacheKey)
            throws SQLException {
        // 4-arg 版本：供 BaseExecutor 模板方法内部调用，直接透传到底层执行器
        return delegate.query(ms, parameter, boundSql, cacheKey);
    }

    @Override
    public void commit(boolean required) throws SQLException {
        // 先把暂存区数据刷入共享二级缓存
        tcm.commit();
        delegate.commit(required);
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        tcm.rollback();
        delegate.rollback(required);
    }

    @Override
    public Transaction getTransaction() {
        return delegate.getTransaction();
    }

    @Override
    public void clearLocalCache() {
        delegate.clearLocalCache();
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameter, BoundSql boundSql) {
        return delegate.createCacheKey(ms, parameter, boundSql);
    }

    @Override
    public void close(boolean forceRollback) throws SQLException {
        delegate.close(forceRollback);
    }

    private void flushCacheIfRequired(MappedStatement ms) {
        Cache cache = ms.getCache();
        if (cache != null && ms.isFlushCacheRequired()) {
            tcm.clear(cache);
        }
    }
}
