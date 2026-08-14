package com.learn.mybatismini.cache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 缓存键 —— <b>决定"两次查询算不算同一次查询"</b>。
 * （对应 MyBatis 的 {@code CacheKey}）
 *
 * <h2>为什么不能简单用 SQL 字符串当 key？</h2>
 * 因为"同一条 SQL + 不同参数"是不同的查询：
 * <pre>
 *   SELECT * FROM t_user WHERE id = ?   参数 1  -> 张三
 *   SELECT * FROM t_user WHERE id = ?   参数 2  -> 李四
 * </pre>
 * 如果只用 SQL 做 key，查 id=2 会错误地命中 id=1 的缓存 —— 这是致命 bug。
 *
 * <h3>MyBatis 的 CacheKey 由 5 个要素共同决定（必背）</h3>
 * <pre>
 *   1. statementId       哪条语句（com.xxx.UserMapper.selectById）
 *   2. RowBounds.offset  分页起点
 *   3. RowBounds.limit   分页条数
 *   4. SQL 文本          带 ? 的最终 SQL（动态 SQL 会导致它变化）
 *   5. 所有参数值        按顺序的每个实参
 * </pre>
 * 五者<b>全部相同</b>才算同一个 key。这解释了两个常见现象：
 * <ul>
 *   <li>同样的查询方法，参数变了就不会命中缓存；</li>
 *   <li>动态 SQL 条件不同时，即使方法名相同也不会命中（因为 SQL 文本变了）。</li>
 * </ul>
 *
 * <h3>hashCode 的增量计算</h3>
 * 每 {@link #update} 一个元素就把它揉进 hashcode：
 * <pre>
 *   hashcode = multiplier * hashcode + baseHashCode
 * </pre>
 * 同时累加 checksum 与 count。这样即使两个 key 的 hashCode 偶然相同，
 * {@link #equals} 还会逐个元素比对，杜绝哈希碰撞导致的<b>缓存串数据</b>。
 * 这种"多重校验"的思路在做分布式缓存 key 设计时很值得借鉴。
 *
 * @author mybatis-source-learn
 */
public class CacheKey {

    private static final int DEFAULT_MULTIPLIER = 37;
    private static final int DEFAULT_HASHCODE = 17;

    private final int multiplier;
    private int hashcode;
    private long checksum;
    private int count;

    /** 参与计算 key 的所有元素，equals 时逐个比对 */
    private final List<Object> updateList;

    public CacheKey() {
        this.hashcode = DEFAULT_HASHCODE;
        this.multiplier = DEFAULT_MULTIPLIER;
        this.count = 0;
        this.updateList = new ArrayList<>();
    }

    /**
     * 追加一个参与计算的元素。
     */
    public void update(Object object) {
        int baseHashCode = (object == null) ? 1 : arrayAwareHashCode(object);

        count++;
        checksum += baseHashCode;
        // 让 hashCode 随元素个数放大，减少碰撞
        baseHashCode *= count;
        hashcode = multiplier * hashcode + baseHashCode;

        updateList.add(object);
    }

    /** 批量追加 */
    public void updateAll(Object[] objects) {
        for (Object o : objects) {
            update(o);
        }
    }

    /** 数组类型要用 Arrays.hashCode，否则拿到的是对象地址哈希 */
    private int arrayAwareHashCode(Object object) {
        if (object.getClass().isArray()) {
            if (object instanceof Object[] arr) {
                return Arrays.deepHashCode(arr);
            }
            if (object instanceof long[] arr) {
                return Arrays.hashCode(arr);
            }
            if (object instanceof int[] arr) {
                return Arrays.hashCode(arr);
            }
            if (object instanceof byte[] arr) {
                return Arrays.hashCode(arr);
            }
        }
        return object.hashCode();
    }

    public int getUpdateCount() {
        return count;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CacheKey cacheKey)) {
            return false;
        }
        // 三层快速否定：hashcode -> checksum -> count，都相同才逐元素比对
        if (hashcode != cacheKey.hashcode
                || checksum != cacheKey.checksum
                || count != cacheKey.count) {
            return false;
        }
        for (int i = 0; i < updateList.size(); i++) {
            Object thisObject = updateList.get(i);
            Object thatObject = cacheKey.updateList.get(i);
            if (!java.util.Objects.deepEquals(thisObject, thatObject)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(hashcode).append(':').append(checksum);
        for (Object o : updateList) {
            sb.append(':').append(o);
        }
        return sb.toString();
    }
}
