package com.example.redislab.common;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级计数器：供前端「肉眼观测」各场景效果（命中/未命中/被拦截/回源次数等）。
 * 教学用，单实例内存计数即可；生产可换成 Micrometer + Prometheus。
 */
@Service
public class StatService {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public void incr(String key) {
        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public long get(String key) {
        AtomicLong v = counters.get(key);
        return v == null ? 0L : v.get();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> map = new LinkedHashMap<>();
        counters.forEach((k, v) -> map.put(k, v.get()));
        return map;
    }

    public void reset() {
        counters.clear();
    }

    /**
     * 按前缀重置计数：让单个演练场景可以独立「重置演示」而不清空其它场景的观测数据。
     * 例：resetByPrefix("seckill:") 只清秒杀场景的指标。
     */
    public void resetByPrefix(String prefix) {
        if (prefix == null) return;
        counters.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
