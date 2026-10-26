package com.learn.mybatismini.session.defaults;

import com.learn.mybatismini.exceptions.PersistenceException;
import com.learn.mybatismini.executor.Executor;
import com.learn.mybatismini.mapping.MappedStatement;
import com.learn.mybatismini.session.Configuration;
import com.learn.mybatismini.session.SqlSession;

import java.util.List;

/**
 * 默认 SqlSession 实现（对应 MyBatis 的 {@code DefaultSqlSession}）。
 *
 * <h3>它几乎不干活，只做"转发 + 簿记"</h3>
 * <ul>
 *   <li>把 select/insert/update/delete 翻译成 Executor 的 query/update；</li>
 *   <li>记一个 {@code dirty} 标记：发生过写操作就置 true，close 时据此决定回滚还是提交；</li>
 *   <li>getMapper 委托给 Configuration 的 MapperRegistry。</li>
 * </ul>
 * 真正的脏活累活在 Executor / Handler 里，这正体现了"门面"该有的克制。
 */
public class DefaultSqlSession implements SqlSession {

    private final Configuration configuration;

    private final Executor executor;

    private final boolean autoCommit;

    /** 是否发生过未提交的写操作 */
    private boolean dirty;

    public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
        this.configuration = configuration;
        this.executor = executor;
        this.autoCommit = autoCommit;
    }

    @Override
    public <T> T selectOne(String statement) {
        return selectOne(statement, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T selectOne(String statement, Object parameter) {
        List<T> list = selectList(statement, parameter);
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() > 1) {
            throw new PersistenceException("期望返回 1 条，实际返回 " + list.size() + " 条：" + statement
                    + "（请用 selectList 接收集合）");
        }
        return list.get(0);
    }

    @Override
    public <E> List<E> selectList(String statement) {
        return selectList(statement, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> selectList(String statement, Object parameter) {
        try {
            MappedStatement ms = configuration.getMappedStatement(statement);
            return executor.query(ms, parameter);
        } catch (Exception e) {
            throw new PersistenceException("执行查询失败：" + statement, e);
        }
    }

    @Override
    public int insert(String statement, Object parameter) {
        return update(statement, parameter);
    }

    @Override
    public int update(String statement, Object parameter) {
        try {
            dirty = true;
            MappedStatement ms = configuration.getMappedStatement(statement);
            return executor.update(ms, parameter);
        } catch (Exception e) {
            throw new PersistenceException("执行更新失败：" + statement, e);
        }
    }

    @Override
    public int delete(String statement, Object parameter) {
        return update(statement, parameter);
    }

    @Override
    public <T> T getMapper(Class<T> type) {
        return configuration.getMapperRegistry().getMapper(type, this);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void commit() {
        try {
            executor.commit(true);
            dirty = false;
        } catch (Exception e) {
            throw new PersistenceException("提交事务失败", e);
        }
    }

    @Override
    public void rollback() {
        try {
            executor.rollback(true);
            dirty = false;
        } catch (Exception e) {
            throw new PersistenceException("回滚事务失败", e);
        }
    }

    @Override
    public void close() {
        try {
            // dirty 为 true 说明有未提交的写操作，关闭时回滚，避免脏数据入库
            executor.close(dirty && !autoCommit);
        } catch (Exception e) {
            throw new PersistenceException("关闭 SqlSession 失败", e);
        }
    }
}
