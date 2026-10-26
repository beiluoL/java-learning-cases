package com.learn.mybatismini.cache;

/**
 * 缓存抽象（对应 MyBatis 的 {@code org.apache.ibatis.cache.Cache}）。
 *
 * <h2>一个接口，撑起 MyBatis 整个缓存体系</h2>
 * 接口只有 5 个方法，但通过<b>装饰器模式</b>层层包装，可以组合出各种能力：
 * <pre>
 *   PerpetualCache                  基础实现：一个 HashMap，永不淘汰
 *      ↓ 被 LruCache 包装
 *   LruCache(PerpetualCache)        加上"超过容量淘汰最久未用"的能力
 *      ↓ 被 TransactionalCache 包装
 *   TransactionalCache(LruCache)    加上"事务提交后才真正写入"的能力
 * </pre>
 * 每一层只关心自己那一件事，互不侵入 —— 想加"同步""序列化""定时清空"，
 * 再写一个装饰器套上去即可。真实 MyBatis 有 9 个装饰器，原理完全相同。
 *
 * <h3>这也是"为什么可以接 Redis 做二级缓存"的答案</h3>
 * 实现这个接口把数据存到 Redis，配到 {@code <cache type="xxx.RedisCache"/>}，
 * 二级缓存立刻变成分布式的。mybatis-redis 官方扩展就是这么做的。
 *
 * @author mybatis-source-learn
 */
public interface Cache {

    /** 缓存唯一标识。二级缓存里就是 namespace（Mapper 接口全限定名） */
    String getId();

    /** 存入 */
    void putObject(Object key, Object value);

    /** 取出，不存在返回 null */
    Object getObject(Object key);

    /** 移除单个（真实 MyBatis 主要给回滚场景用） */
    Object removeObject(Object key);

    /** 清空整个缓存 */
    void clear();

    /** 当前条目数 */
    int getSize();
}
