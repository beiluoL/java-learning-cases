package com.learn.mybatismini.cache.decorators;

import com.learn.mybatismini.cache.Cache;

import java.util.Deque;
import java.util.LinkedList;

/**
 * FIFO 淘汰装饰器（对应 MyBatis 的 {@code FifoCache}）。
 *
 * <p>与 {@link LruCache} 的区别只有一点：
 * <pre>
 *   LRU  按"最近是否被<b>访问</b>"淘汰（get 也会刷新位置）
 *   FIFO 按"进入缓存的<b>先后</b>"淘汰（get 不影响顺序）
 * </pre>
 * 写两个装饰器就能对比着看清"缓存淘汰策略"到底差在哪 ——
 * 这也正是装饰器模式的好处：策略是可替换的零件。
 *
 * @author mybatis-source-learn
 */
public class FifoCache implements Cache {

    private final Cache delegate;

    /** 记录 key 进入的顺序 */
    private final Deque<Object> keyList = new LinkedList<>();

    private final int size;

    public FifoCache(Cache delegate) {
        this(delegate, 1024);
    }

    public FifoCache(Cache delegate, int size) {
        this.delegate = delegate;
        this.size = size;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public synchronized void putObject(Object key, Object value) {
        cycleKeyList(key);
        delegate.putObject(key, value);
    }

    @Override
    public synchronized Object getObject(Object key) {
        // 注意：FIFO 不因读取而调整顺序，这是与 LRU 的本质差异
        return delegate.getObject(key);
    }

    @Override
    public synchronized Object removeObject(Object key) {
        keyList.remove(key);
        return delegate.removeObject(key);
    }

    @Override
    public synchronized void clear() {
        delegate.clear();
        keyList.clear();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    private void cycleKeyList(Object key) {
        keyList.addLast(key);
        if (keyList.size() > size) {
            Object oldestKey = keyList.removeFirst();
            delegate.removeObject(oldestKey);
        }
    }

    @Override
    public String toString() {
        return "FifoCache(size=" + size + ") -> " + delegate;
    }
}
