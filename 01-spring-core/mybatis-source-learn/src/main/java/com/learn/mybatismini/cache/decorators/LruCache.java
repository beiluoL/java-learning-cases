package com.learn.mybatismini.cache.decorators;

import com.learn.mybatismini.cache.Cache;
import com.learn.mybatismini.logging.MiniLog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU 淘汰装饰器（对应 MyBatis 的 {@code LruCache}）。
 *
 * <h2>装饰器模式的标准实现，请对照结构理解</h2>
 * <pre>
 *   LruCache implements Cache        ← 与被装饰者实现同一接口（可互换）
 *      private Cache delegate;       ← 持有被装饰者（组合，非继承）
 *
 *      putObject(k, v) {
 *          delegate.putObject(k, v); // 转发原有能力
 *          cycleKeyList(k);          // 附加新能力：记录访问顺序、必要时淘汰
 *      }
 * </pre>
 * 关键点：<b>装饰器与被装饰对象实现同一接口</b>，所以上层完全感知不到差别，
 * 却凭空多了 LRU 能力。这比"写一个 LruPerpetualCache 子类"灵活得多 ——
 * 装饰器可以任意叠加顺序、任意组合。
 *
 * <h3>LRU 的实现技巧：LinkedHashMap 的隐藏能力</h3>
 * 不用自己写双向链表 + HashMap！{@link LinkedHashMap} 构造器第三个参数
 * {@code accessOrder=true} 时，<b>get 也会把元素移到链表末尾</b>，
 * 再重写 {@link LinkedHashMap#removeEldestEntry} 就得到一个现成的 LRU 结构。
 * 这个技巧在面试手写 LRU 时非常好用。
 *
 * @author mybatis-source-learn
 */
public class LruCache implements Cache {

    /** 被装饰的真实缓存 */
    private final Cache delegate;

    /** 借 LinkedHashMap 维护访问顺序，value 存的就是 key 本身（只用它的顺序能力） */
    private Map<Object, Object> keyMap;

    /** 需要淘汰的那个最久未使用的 key */
    private Object eldestKey;

    /** 容量上限 */
    private int size;

    public LruCache(Cache delegate) {
        this(delegate, 1024);
    }

    public LruCache(Cache delegate, int size) {
        this.delegate = delegate;
        setSize(size);
    }

    /**
     * 设置容量并重建 LRU 结构。
     */
    public final void setSize(int size) {
        this.size = size;
        // accessOrder = true -> 访问（get）也算"使用"，元素会被移到末尾
        this.keyMap = new LinkedHashMap<>(size, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                boolean tooBig = size() > LruCache.this.size;
                if (tooBig) {
                    // 记下要淘汰的 key，由外层在 putObject 里从 delegate 中移除
                    LruCache.this.eldestKey = eldest.getKey();
                }
                return tooBig;
            }
        };
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public synchronized void putObject(Object key, Object value) {
        delegate.putObject(key, value);
        cycleKeyList(key);
    }

    @Override
    public synchronized Object getObject(Object key) {
        // ★ 这一句是 LRU 的核心：即使只是读，也要刷新它的"最近使用"位置
        keyMap.get(key);
        return delegate.getObject(key);
    }

    @Override
    public synchronized Object removeObject(Object key) {
        keyMap.remove(key);
        return delegate.removeObject(key);
    }

    @Override
    public synchronized void clear() {
        delegate.clear();
        keyMap.clear();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    /** 记录访问顺序；若触发容量上限，把最久未用的条目从真实缓存里删掉 */
    private void cycleKeyList(Object key) {
        keyMap.put(key, key);
        if (eldestKey != null) {
            delegate.removeObject(eldestKey);
            MiniLog.trace("LruCache 淘汰最久未使用的条目（容量上限 " + size + "）");
            eldestKey = null;
        }
    }

    @Override
    public String toString() {
        return "LruCache(size=" + size + ") -> " + delegate;
    }
}
