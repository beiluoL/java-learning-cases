package com.learn.mybatismini.executor;

import com.learn.mybatismini.cache.CacheKey;
import com.learn.mybatismini.cache.PerpetualCache;
import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.logging.MiniLog;
import com.learn.mybatismini.mapping.BoundSql;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.reflection.MetaObject;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.transaction.Transaction;
import com.learn.mybatismini.type.TypeHandlerRegistry;

import java.sql.SQLException;
import java.util.List;

/**
 * 执行器基类（对应 MyBatis 的 {@code BaseExecutor}）。
 *
 * <h3>模板方法：把"不变的部分"固定，"会变的部分"留钩子</h3>
 * 不变的部分（本类实现）：
 * <ul>
 *   <li><b>一级缓存</b>（localCache，一个 PerpetualCache，key = CacheKey）；</li>
 *   <li>缓存键的生成规则（statementId+offset+limit+sql+参数）；</li>
 *   <li>update 前清空一级缓存；</li>
 *   <li>close 时清缓存。</li>
 * </ul>
 * 会变的部分（子类实现）：{@link #doQuery} / {@link #doUpdate}（具体怎么和 JDBC 打交道）。
 *
 * <p><b>一级缓存为什么只在同一个 SqlSession 内有效？</b>
 * 因为 localCache 就活在 BaseExecutor 实例里，而一个 SqlSession 独占一个 Executor。
 * 换一个 SqlSession（新 Executor）就有新 localCache，自然互相看不见。
 */
public abstract class BaseExecutor implements Executor {

    /** 一级缓存（会话级）：同一个 SqlSession 内相同查询命中，不查库 */
    protected PerpetualCache localCache;

    protected final Configuration configuration;

    protected final Transaction transaction;

    /** 标记是否已关闭 */
    protected boolean closed;

    public BaseExecutor(Configuration configuration, Transaction transaction) {
        this.configuration = configuration;
        this.transaction = transaction;
        this.localCache = new PerpetualCache("LocalCache");
        this.closed = false;
    }

    @Override
    public int update(MappedStatement ms, Object parameter) throws SQLException {
        if (closed) {
            throw new PersistenceException("Executor 已关闭，不能执行更新");
        }
        // 写操作前清空一级缓存，保证读到最新数据
        clearLocalCache();
        return doUpdate(ms, parameter);
    }

    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter) throws SQLException {
        // 先算出 BoundSql 与缓存键，再交给重载方法（既供子类，也供 CachingExecutor 复用）
        BoundSql boundSql = ms.getBoundSql(parameter);
        CacheKey key = createCacheKey(ms, parameter, boundSql);
        return query(ms, parameter, boundSql, key);
    }

    /**
     * 带缓存键的查询（模板方法主体）。
     */
    @SuppressWarnings("unchecked")
    public <E> List<E> query(MappedStatement ms, Object parameter, BoundSql boundSql, CacheKey key)
            throws SQLException {
        if (closed) {
            throw new PersistenceException("Executor 已关闭，不能执行查询");
        }
        List<E> list;
        // 1) 先查一级缓存
        Object cached = localCache.getObject(key);
        if (cached != null) {
            list = (List<E>) cached;
            MiniLog.cache("命中一级缓存，直接返回（未查库） -> " + ms.getId());
        } else {
            MiniLog.cache("未命中一级缓存，查询数据库 -> " + ms.getId());
            // 2) 缓存未命中 -> 真正查库
            list = doQuery(ms, parameter, boundSql);
            // 3) 把结果写进一级缓存
            localCache.putObject(key, list);
        }
        return list;
    }

    @Override
    public CacheKey createCacheKey(MappedStatement ms, Object parameter, BoundSql boundSql) {
        CacheKey cacheKey = new CacheKey();
        cacheKey.update(ms.getId());
        // offset / limit：本简化版不分页，固定为 0 / MAX（保留位，与 MyBatis 对齐）
        cacheKey.update(0);
        cacheKey.update(Integer.MAX_VALUE);
        cacheKey.update(boundSql.getSql());
        // 把每个参数值揉进 key：参数不同 => key 不同 => 不会命中错误缓存
        List<com.learn.mybatismini.mapping.ParameterMapping> mappings = boundSql.getParameterMappings();
        TypeHandlerRegistry thr = configuration.getTypeHandlerRegistry();
        if (mappings != null) {
            MetaObject metaObject = configuration.newMetaObject(parameter);
            for (com.learn.mybatismini.mapping.ParameterMapping pm : mappings) {
                String property = pm.getProperty();
                Object value;
                if (boundSql.hasAdditionalParameter(property)) {
                    value = boundSql.getAdditionalParameter(property);
                } else if (parameter == null) {
                    value = null;
                } else if (thr.hasTypeHandler(parameter.getClass())) {
                    // 单值基本类型（Long/Integer/...）：整对象就是参数值
                    value = parameter;
                } else {
                    value = metaObject.getValue(property);
                }
                cacheKey.update(value);
            }
        }
        return cacheKey;
    }

    @Override
    public void commit(boolean required) throws SQLException {
        if (closed) {
            throw new PersistenceException("Executor 已关闭，无法提交");
        }
        if (required) {
            transaction.commit();
        }
        // 提交后清空一级缓存（事务结束，缓存结果已落库，刷新以保一致）
        clearLocalCache();
    }

    @Override
    public void rollback(boolean required) throws SQLException {
        if (!closed && required) {
            transaction.rollback();
        }
        clearLocalCache();
    }

    @Override
    public Transaction getTransaction() {
        if (closed) {
            throw new PersistenceException("Executor 已关闭");
        }
        return transaction;
    }

    @Override
    public void clearLocalCache() {
        if (!closed) {
            localCache.clear();
        }
    }

    @Override
    public void close(boolean forceRollback) throws SQLException {
        try {
            if (!closed) {
                if (forceRollback) {
                    transaction.rollback();
                } else {
                    // 未提交的事务在关闭时回滚，避免脏连接归还到池
                    transaction.rollback();
                }
            }
        } finally {
            transaction.close();
            closed = true;
            localCache = null;
        }
    }

    // ==================================================================
    // 子类必须实现的钩子
    // ==================================================================

    protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

    protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, BoundSql boundSql)
            throws SQLException;
}
