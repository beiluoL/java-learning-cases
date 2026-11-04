package com.example.redislab.common;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 统一的 Redis Key 命名 + TTL 生成工具。
 * 规范：用业务前缀区分命名空间，避免 key 冲突；TTL 统一带随机抖动防雪崩。
 */
public final class RedisKeys {

    private RedisKeys() {}

    // ---------- 各场景 key ----------
    public static String cacheProduct(Long id)            { return "cache:product:" + id; }
    public static String rankDay(String day)              { return "rank:day:" + day; }
    public static String rankTotal()                      { return "rank:total"; }
    public static String ratelimitWindow(String key)      { return "ratelimit:window:" + key; }
    public static String ratelimitFixed(String key)       { return "ratelimit:fixed:" + key; }
    public static String pubsubChannel()                  { return "pubsub:demo"; }
    public static String streamChannel()                  { return "stream:demo"; }
    public static String penetrationProduct(Long id)      { return "penetration:product:" + id; }
    public static String breakdownHot(Long id)           { return "breakdown:hot:" + id; }
    public static String avalancheProduct(Long id)        { return "avalanche:product:" + id; }
    public static String lockStock(Long productId)        { return "lock:stock:" + productId; }
    public static String lockMutex(String biz)            { return "lock:mutex:" + biz; }

    /**
     * 防雪崩核心：基础 TTL + 随机抖动。
     * 若所有 key 用同一固定 TTL，会同一时刻集体失效 -> 雪崩。
     * 给每个 key 叠加一个随机秒数，把失效时间打散到一段区间。
     */
    public static long ttlWithJitter(long baseSeconds, long jitterSeconds) {
        if (jitterSeconds <= 0) return baseSeconds;
        return baseSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds);
    }
}
