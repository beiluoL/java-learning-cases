package com.example.redislab.modules.idempotent;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 场景12：幂等处理（Redis 一次性 Token + MySQL 唯一索引双保险）。
 *
 * <p>三道防线，覆盖两类重复：
 * 1) Redis Token：调用前先领 token，提交时用 Lua 原子「存在则删除」——
 *    并发 N 次携带同一 token 的请求，只有一个能拿到 1，其余全部判定为重复；
 * 2) 已消费标记：token 删除后写入 used 标记（带 TTL），用来区分「重复提交」与
 *    「token 压根不存在/已过期」，也能防短时间内的重放；
 * 3) MySQL 唯一索引：idempotent_record(biz_id) UNIQUE，挡住「换了新 token、
 *    但业务单号相同」的重复提交——这是 Redis 方案覆盖不到的边界。
 *
 * <p>幂等与事务边界：真正的生产实现应把「写业务表 + 写去重表」放在同一个本地事务里，
 * 这里用单表 INSERT 演示唯一索引的兜底语义。</p>
 */
@Service
public class IdempotentService {

    /** KEYS: 1=token key 2=used 标记 key；ARGV: 1=nowMs 2=used TTL(ms)。返回 1=首次消费 0=已消费/不存在 */
    private static final String CONSUME_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              redis.call('DEL', KEYS[1])
              redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2])
              return 1
            end
            return 0
            """;

    private static final long TOKEN_TTL_SECONDS = 300;
    private static final long USED_TTL_MS = 300_000L;

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final StatService statService;
    private final RedisScript<Long> consumeScript;

    public IdempotentService(StringRedisTemplate stringRedisTemplate,
                             JdbcTemplate jdbcTemplate,
                             StatService statService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.statService = statService;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_LUA, Long.class);
    }

    /** 签发一次性 token（业务方调用接口前先领） */
    public Map<String, Object> createToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                RedisKeys.idempotentToken(token), "1", Duration.ofSeconds(TOKEN_TTL_SECONDS));
        statService.incr("idempotent:token_issued");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("ttlSeconds", TOKEN_TTL_SECONDS);
        m.put("note", "该 token 只能成功消费一次；请多次用它提交，观察只有第一次生效");
        return m;
    }

    /**
     * 提交业务请求。result 取值：
     * FIRST=首次处理成功 / DUPLICATE=同 token 重复 / DUPLICATE_DB=换 token 但同 bizId /
     * INVALID=token 不存在或已过期。
     */
    public Map<String, Object> submit(String token, String bizId, BigDecimal amount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bizId", bizId);
        m.put("token", token);

        Long consumed = stringRedisTemplate.execute(
                consumeScript,
                Arrays.asList(RedisKeys.idempotentToken(token), RedisKeys.idempotentUsed(token)),
                String.valueOf(System.currentTimeMillis()), String.valueOf(USED_TTL_MS));

        if (consumed != null && consumed == 1L) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO idempotent_record (biz_id, token, amount, create_time) VALUES (?, ?, ?, ?)",
                        bizId, token, amount, System.currentTimeMillis());
                statService.incr("idempotent:first");
                m.put("result", "FIRST");
                m.put("msg", "首次请求：业务处理成功，已写入去重表");
            } catch (DuplicateKeyException e) {
                statService.incr("idempotent:db_dedup");
                m.put("result", "DUPLICATE_DB");
                m.put("msg", "换了新 token 但 bizId 重复，被 MySQL 唯一索引兜底拦截（Redis token 挡不住这类）");
            }
        } else {
            boolean everUsed = Boolean.TRUE.equals(
                    stringRedisTemplate.hasKey(RedisKeys.idempotentUsed(token)));
            if (everUsed) {
                statService.incr("idempotent:duplicate");
                m.put("result", "DUPLICATE");
                m.put("msg", "重复请求：同一 token 已被消费，直接返回上次结果，不再执行业务");
            } else {
                statService.incr("idempotent:invalid");
                m.put("result", "INVALID");
                m.put("msg", "token 不存在或已过期，请先领取新 token");
            }
        }
        return m;
    }

    /**
     * 重复请求模拟：真正并发发起 N 次「完全相同」的提交，验证只有 1 次生效。
     * 用 CountDownLatch 让所有线程同一时刻起跑，避免有线程在别的线程完成后才发。
     */
    public Map<String, Object> replay(String token, String bizId, BigDecimal amount, int times) {
        int n = Math.min(Math.max(times, 1), 200);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(n, 32));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return submit(token, bizId, amount);
            }));
        }
        start.countDown();

        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Future<Map<String, Object>> f : futures) {
            try {
                String r = String.valueOf(f.get().get("result"));
                tally.merge(r, 1, Integer::sum);
            } catch (Exception e) {
                tally.merge("ERROR", 1, Integer::sum);
            }
        }
        pool.shutdown();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRequests", n);
        m.put("tally", tally);
        m.put("effectiveRequests", tally.getOrDefault("FIRST", 0) + tally.getOrDefault("DUPLICATE_DB", 0));
        m.put("note", "并发 " + n + " 次相同请求，只有 1 次真正执行业务，其余被幂等拦截");
        return m;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tokenIssued", statService.get("idempotent:token_issued"));
        m.put("firstProcessed", statService.get("idempotent:first"));
        m.put("duplicateBlocked", statService.get("idempotent:duplicate"));
        m.put("dbDedupBlocked", statService.get("idempotent:db_dedup"));
        m.put("invalidToken", statService.get("idempotent:invalid"));
        Integer db = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotent_record", Integer.class);
        m.put("dbRecords", db == null ? 0 : db);
        return m;
    }

    public List<Map<String, Object>> records() {
        return jdbcTemplate.queryForList(
                "SELECT * FROM idempotent_record ORDER BY id DESC LIMIT 50");
    }

    /** 清空演示数据（含 DB 去重表），便于重新演示 */
    public void reset() {
        jdbcTemplate.update("DELETE FROM idempotent_record");
        statService.resetByPrefix("idempotent:");
    }
}
