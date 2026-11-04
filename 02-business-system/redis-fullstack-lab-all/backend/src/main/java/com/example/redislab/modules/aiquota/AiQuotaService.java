package com.example.redislab.modules.aiquota;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 场景13：AI 额度管理（Redis + Lua 单 ZSET 双滑动窗口 RPM / TPM）。
 *
 * <p>大模型网关要同时限制两个维度：RPM（每分钟请求数）与 TPM（每分钟 token 数）。
 * 若分成两个计数器分别判断，会出现「A 过了 RPM、B 过了 TPM」的并发不一致。
 * 本项目用**一个 ZSET + 一段 Lua** 原子完成「清理窗口外记录 → 统计当前 RPM/TPM
 * → 双维度校验 → 写入」，彻底避免竞态。
 *
 * <p>window member 格式：{uuid}|{tokens}。score = 请求时间戳(ms)，
 * 清窗口用 ZREMRANGEBYSCORE，统计 TPM 时遍历窗口内 member 累加 tokens。
 *
 * <p>超额降级：RPM 超限返回 -1、TPM 超限返回 -2，业务侧可据此走
 * 「排队等待 / 降级到小模型 / 拒绝并返回 429」三种策略，而不是简单报错。</p>
 */
@Service
public class AiQuotaService {

    private static final long WINDOW_MS = 60_000L;

    /** KEYS:1=窗口ZSET；ARGV:1=now 2=window 3=rpmLimit 4=tpmLimit 5=tokens 6=member */
    private static final String QUOTA_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local rpmLimit = tonumber(ARGV[3])
            local tpmLimit = tonumber(ARGV[4])
            local tokens = tonumber(ARGV[5])
            local member = ARGV[6]

            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local items = redis.call('ZRANGE', key, 0, -1)
            local cnt = 0
            local tks = 0
            for i = 1, #items do
              cnt = cnt + 1
              local v = items[i]
              local p = string.find(v, '|')
              if p then
                tks = tks + tonumber(string.sub(v, p + 1))
              end
            end

            if cnt + 1 > rpmLimit then
              return -1
            end
            if tks + tokens > tpmLimit then
              return -2
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return 1
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final StatService statService;
    private final RedisScript<Long> quotaScript;

    public AiQuotaService(StringRedisTemplate stringRedisTemplate, StatService statService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.statService = statService;
        this.quotaScript = new DefaultRedisScript<>(QUOTA_LUA, Long.class);
    }

    /** 配置用户额度（存 Redis Hash，多实例共享） */
    public Map<String, Object> config(String userId, int rpm, int tpm) {
        int r = Math.max(1, Math.min(rpm, 10000));
        int t = Math.max(1, Math.min(tpm, 10_000_000));
        Map<String, String> cfg = new LinkedHashMap<>();
        cfg.put("rpm", String.valueOf(r));
        cfg.put("tpm", String.valueOf(t));
        stringRedisTemplate.opsForHash().putAll(RedisKeys.aiquotaConfig(userId), cfg);
        return readConfig(userId);
    }

    /** 消费一次调用额度 */
    public Map<String, Object> consume(String userId, int tokens) {
        Map<String, Object> cfg = readConfig(userId);
        int rpmLimit = (int) cfg.get("rpm");
        int tpmLimit = (int) cfg.get("tpm");
        long now = System.currentTimeMillis();
        String member = UUID.randomUUID().toString().replace("-", "") + "|" + tokens;

        Long code = stringRedisTemplate.execute(
                quotaScript,
                Collections.singletonList(RedisKeys.aiquotaWindow(userId)),
                String.valueOf(now), String.valueOf(WINDOW_MS),
                String.valueOf(rpmLimit), String.valueOf(tpmLimit),
                String.valueOf(tokens), member);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("tokens", tokens);
        m.put("windowMs", WINDOW_MS);
        if (code != null && code == 1L) {
            statService.incr("aiquota:allowed");
            statService.incr("aiquota:tokens:" + userId); // 简化打点，精确值见 status
            m.put("result", "ALLOWED");
            m.put("degraded", false);
            m.put("msg", "额度充足，请求放行");
        } else if (code != null && code == -1L) {
            statService.incr("aiquota:rpm_block");
            m.put("result", "RPM_EXCEEDED");
            m.put("degraded", true);
            m.put("msg", "RPM 超限：建议排队等待或降级（429）");
        } else if (code != null && code == -2L) {
            statService.incr("aiquota:tpm_block");
            m.put("result", "TPM_EXCEEDED");
            m.put("degraded", true);
            m.put("msg", "TPM 超限：建议降级到小模型 / 截断上下文 / 拒绝");
        } else {
            statService.incr("aiquota:error");
            m.put("result", "ERROR");
            m.put("degraded", true);
            m.put("msg", "Redis 脚本执行异常");
        }
        return m;
    }

    /** 突发流量模拟：并发 N 次调用，观察额度水位与超额降级 */
    public Map<String, Object> stress(String userId, int tokens, int times) {
        int n = Math.min(Math.max(times, 1), 200);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(n, 32));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return consume(userId, tokens);
            }));
        }
        start.countDown();
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Future<Map<String, Object>> f : futures) {
            try {
                tally.merge(String.valueOf(f.get().get("result")), 1, Integer::sum);
            } catch (Exception e) {
                tally.merge("ERROR", 1, Integer::sum);
            }
        }
        pool.shutdown();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRequests", n);
        m.put("tokensPerRequest", tokens);
        m.put("tally", tally);
        m.put("usage", usage(userId));
        return m;
    }

    /** 当前窗口用量（可观测性：水位） */
    public Map<String, Object> usage(String userId) {
        Map<String, Object> cfg = readConfig(userId);
        int rpmLimit = (int) cfg.get("rpm");
        int tpmLimit = (int) cfg.get("tpm");
        long now = System.currentTimeMillis();
        Set<String> items = stringRedisTemplate.opsForZSet().rangeByScore(
                RedisKeys.aiquotaWindow(userId), now - WINDOW_MS, now);
        int usedRpm = items == null ? 0 : items.size();
        long usedTpm = 0;
        if (items != null) {
            for (String v : items) {
                int p = v.indexOf('|');
                if (p >= 0) {
                    try {
                        usedTpm += Long.parseLong(v.substring(p + 1));
                    } catch (NumberFormatException ignored) {
                        // 忽略脏数据
                    }
                }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rpmLimit", rpmLimit);
        m.put("rpmUsed", usedRpm);
        m.put("rpmPercent", rpmLimit == 0 ? 0 : Math.round(usedRpm * 100.0 / rpmLimit));
        m.put("tpmLimit", tpmLimit);
        m.put("tpmUsed", usedTpm);
        m.put("tpmPercent", tpmLimit == 0 ? 0 : Math.round(usedTpm * 100.0 / tpmLimit));
        return m;
    }

    public Map<String, Object> status(String userId) {
        Map<String, Object> m = new LinkedHashMap<>(usage(userId));
        m.put("userId", userId);
        m.put("allowed", statService.get("aiquota:allowed"));
        m.put("rpmBlocked", statService.get("aiquota:rpm_block"));
        m.put("tpmBlocked", statService.get("aiquota:tpm_block"));
        m.put("errors", statService.get("aiquota:error"));
        return m;
    }

    /** 清空该用户窗口，便于重新演示 */
    public void reset(String userId) {
        stringRedisTemplate.delete(RedisKeys.aiquotaWindow(userId));
        statService.resetByPrefix("aiquota:");
    }

    private Map<String, Object> readConfig(String userId) {
        Map<Object, Object> raw = stringRedisTemplate.opsForHash()
                .entries(RedisKeys.aiquotaConfig(userId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("rpm", parseInt(raw.get("rpm"), 10));
        m.put("tpm", parseInt(raw.get("tpm"), 1000));
        return m;
    }

    private int parseInt(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
