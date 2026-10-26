package com.learn.mybatismini.cache;

import java.util.HashMap;
import java.util.Map;

/**
 * 永久缓存：最基础的 Cache 实现，本质就是一个 HashMap。
 * （对应 MyBatis 的 {@code PerpetualCache}）
 *
 * <h3>它扮演两个角色</h3>
 * <ol>
 *   <li><b>一级缓存的实体</b>：BaseExecutor 里的 {@code localCache} 就是它，
 *       随 SqlSession 创建、随 SqlSession 销毁；</li>
 *   <li><b>二级缓存的最内层</b>：真正存数据的地方，外面再套 LruCache
 *       等装饰器提供淘汰能力。</li>
 * </ol>
 *
 * <h3>为什么它不做任何淘汰？</h3>
 * 这是<b>单一职责原则</b>的体现：它只负责"存和取"。
 * 淘汰是另一件事，交给装饰器。于是一级缓存可以直接用它（会话很短，不需要淘汰），
 * 二级缓存则套上 LruCache（长期存活，必须限容量防 OOM）。
 *
 * <h3>线程安全说明</h3>
 * 内部用普通 HashMap，<b>不是线程安全的</b>。这没问题，因为：
 * <ul>
 *   <li>一级缓存归属单个 SqlSession，而 SqlSession 本身就不允许多线程共享；</li>
 *   <li>二级缓存的并发安全由外层装饰器（真实 MyBatis 的 SynchronizedCache）
 *       或 CachingExecutor 的事务缓存机制保证。</li>
 * </ul>
 * 本项目为了简化，直接把外层 LruCache 的方法加了 synchronized。
 *
 * @author mybatis-source-learn
 */
public class PerpetualCache implements Cache {

    private final String id;

    private final Map<Object, Object> cache = new HashMap<>();

    public PerpetualCache(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void putObject(Object key, Object value) {
        cache.put(key, value);
    }

    @Override
    public Object getObject(Object key) {
        return cache.get(key);
    }

    @Override
    public Object removeObject(Object key) {
        return cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public int getSize() {
        return cache.size();
    }

    @Override
    public String toString() {
        return "PerpetualCache{id='" + id + "', size=" + cache.size() + '}';
    }
}
