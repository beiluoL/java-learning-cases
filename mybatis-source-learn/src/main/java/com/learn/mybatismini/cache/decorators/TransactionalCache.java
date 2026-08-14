package com.learn.mybatismini.cache.decorators;

import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.logging.MiniLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 事务性缓存装饰器（对应 MyBatis 的 {@code TransactionalCache}）。
 *
 * <h2>它回答了二级缓存最难的一个问题：脏数据怎么防？</h2>
 *
 * <h3>如果没有它，会发生什么？</h3>
 * <pre>
 *   SessionA: insert 一个用户 -> 立刻写入二级缓存 -> 还没 commit
 *   SessionB: 查询 -> 命中二级缓存 -> 读到了 A 未提交的数据（脏读！）
 *   SessionA: 回滚
 *   -> 缓存里留下一条数据库里根本不存在的记录，且会一直被读到
 * </pre>
 *
 * <h3>解法：写操作先"记账"，commit 时才"过账"</h3>
 * <pre>
 *   putObject(k, v)  ->  只放进临时 Map entriesToAddOnCommit（<b>不进真缓存</b>）
 *   commit()         ->  把临时 Map 一次性刷进真缓存
 *   rollback()       ->  直接丢弃临时 Map，真缓存毫发无损
 * </pre>
 * 这就是二级缓存<b>"必须 commit 之后才生效"</b>的根本原因 ——
 * 很多人第一次用二级缓存发现"没命中"，其实就是忘了 {@code sqlSession.commit()}。
 *
 * <h3>还有一个细节：缓存穿透的记录</h3>
 * 查询未命中时，CachingExecutor 会 {@code putObject(key, null)} 占位。
 * 这里用 {@code entriesMissedInCache} 记录这些 key，
 * 提交时一并写入（值为 null），下次同样的查询就能"命中一个 null"，
 * 避免反复打到数据库 —— 这是框架层面的<b>防缓存穿透</b>设计。
 *
 * @author mybatis-source-learn
 */
public class TransactionalCache implements Cache {

    /** 被装饰的真实二级缓存（通常是 LruCache -> PerpetualCache） */
    private final Cache delegate;

    /** 提交时才写入真缓存的暂存区 */
    private final Map<Object, Object> entriesToAddOnCommit = new HashMap<>();

    /** 本事务内未命中过的 key（用于防穿透占位） */
    private final Set<Object> entriesMissedInCache = new HashSet<>();

    /** 是否在提交时清空整个缓存（执行过 update/insert/delete 时置 true） */
    private boolean clearOnCommit;

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
        this.clearOnCommit = false;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public Object getObject(Object key) {
        // 读：直接穿透到真缓存读（读不会造成脏数据）
        Object object = delegate.getObject(key);
        if (object == null) {
            // 记录未命中，提交时占位
            entriesMissedInCache.add(key);
        }
        // 若本事务内执行过写操作（clearOnCommit=true），则不允许读到旧值
        return clearOnCommit ? null : object;
    }

    @Override
    public void putObject(Object key, Object object) {
        // ★ 写：只进暂存区，不进真缓存
        entriesToAddOnCommit.put(key, object);
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    @Override
    public void clear() {
        // 标记"提交时要清空"，同时丢弃本事务的暂存内容
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
        MiniLog.trace("TransactionalCache 标记 clearOnCommit（本事务有写操作）");
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    // ==================================================================
    // 事务边界方法：由 TransactionalCacheManager 在 SqlSession.commit/rollback 时驱动
    // ==================================================================

    /**
     * 提交：把暂存内容真正刷进二级缓存。
     */
    public void commit() {
        if (clearOnCommit) {
            // 本事务有写操作，为保证一致性直接清空整个 namespace 缓存
            delegate.clear();
            MiniLog.trace("TransactionalCache 提交：清空二级缓存（因本事务有写操作）");
        }
        flushPendingEntries();
        reset();
    }

    /**
     * 回滚：丢弃暂存内容，真缓存不受影响。
     */
    public void rollback() {
        // 回滚时把"未命中占位"也撤掉，避免留下无意义的 null
        unlockMissedEntries();
        MiniLog.trace("TransactionalCache 回滚：丢弃暂存数据，二级缓存未被污染");
        reset();
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }

    /** 把暂存区刷入真缓存 */
    private void flushPendingEntries() {
        for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
            delegate.putObject(entry.getKey(), entry.getValue());
        }
        if (!entriesToAddOnCommit.isEmpty()) {
            MiniLog.trace("TransactionalCache 提交：写入 " + entriesToAddOnCommit.size() + " 条到二级缓存");
        }
        // 未命中的 key 用 null 占位，防止缓存穿透
        for (Object entry : entriesMissedInCache) {
            if (!entriesToAddOnCommit.containsKey(entry)) {
                delegate.putObject(entry, null);
            }
        }
    }

    private void unlockMissedEntries() {
        for (Object entry : entriesMissedInCache) {
            delegate.removeObject(entry);
        }
    }
}
