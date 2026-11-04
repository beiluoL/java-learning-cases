package com.example.redislab.modules.seckill;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景11：秒杀库存（Lua 原子扣减 + 用户限购 + List 异步削峰落库 + DB 唯一约束兜底）。
 *
 * <p>四层设计：
 * 1) 用户限购：SISMEMBER 校验「一人一件」，挡住重复抢购；
 * 2) 原子扣减：整段「限购校验 → 库存校验 → DECR → 记录用户 → 入队」用 Lua 在 Redis
 *    单线程内一次完成，杜绝「读到库存 1、并发都去扣」的超卖；
 * 3) 异步削峰：成功的请求 LPUSH 进 Redis List，后台单线程 rightPop 慢慢落 MySQL，
 *    秒杀瞬间不直接打 DB，把洪峰摊平成稳定流速；
 * 4) DB 兜底：seckill_order(product_id, user_id) 唯一约束，即使 Redis 出现缝隙也不会双写。
 *
 * <p>为什么用 List 而不是 Stream 的 XADD '*'：Lua 脚本内应避免非确定性命令（自动 ID），
 * 而显式 ID 又要求严格递增、服务重启易冲突。LPUSH 是确定性命令，最适合脚本内入队。</p>
 */
@Service
public class SeckillService {

    /** KEYS: 1=库存key 2=限购Set 3=待落单List；ARGV: 1=userId 2=nowMs 3=入队payload */
    private static final String FLASH_LUA = """
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
              return -1
            end
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            if stock <= 0 then
              return 0
            end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('LPUSH', KEYS[3], ARGV[3])
            return 1
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final StatService statService;
    private final RedisScript<Long> flashScript;

    /** 落库延迟观测（异步削峰链路的核心指标） */
    private final AtomicLong persistDelayTotalMs = new AtomicLong();
    private final AtomicLong persistCount = new AtomicLong();
    private volatile boolean running = true;

    public SeckillService(StringRedisTemplate stringRedisTemplate,
                          JdbcTemplate jdbcTemplate,
                          StatService statService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.statService = statService;
        this.flashScript = new DefaultRedisScript<>(FLASH_LUA, Long.class);
    }

    /** 库存预热：把库存写进 Redis（秒杀前必须先预热，绝不能让请求回源 DB 查库存） */
    public Map<String, Object> init(Long productId, int stock) {
        stringRedisTemplate.opsForValue().set(RedisKeys.seckillStock(productId), String.valueOf(stock));
        stringRedisTemplate.delete(RedisKeys.seckillUsers(productId));
        stringRedisTemplate.delete(RedisKeys.seckillPendingQueue());
        jdbcTemplate.update("DELETE FROM seckill_order WHERE product_id = ?", productId);
        statService.resetByPrefix("seckill:");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", productId);
        m.put("stock", stock);
        m.put("note", "库存已预热到 Redis，限购集合与待落单队列已清空");
        return m;
    }

    /** 单次秒杀：一次 Lua 原子完成全部判断 */
    public Map<String, Object> flash(Long productId, String userId) {
        long now = System.currentTimeMillis();
        // payload 用 | 分隔，后台消费者据此落库
        String payload = productId + "|" + userId + "|" + now;
        Long code = stringRedisTemplate.execute(
                flashScript,
                Arrays.asList(RedisKeys.seckillStock(productId),
                        RedisKeys.seckillUsers(productId),
                        RedisKeys.seckillPendingQueue()),
                userId, String.valueOf(now), payload);

        String result;
        String msg;
        if (code == null) {
            result = "ERROR";
            msg = "Redis 脚本执行异常";
            statService.incr("seckill:error");
        } else if (code == 1L) {
            result = "SUCCESS";
            msg = "抢购成功，订单已入异步落单队列（削峰）";
            statService.incr("seckill:success");
        } else if (code == 0L) {
            result = "SOLDOUT";
            msg = "已售罄（Lua 内快速失败，不回源 DB）";
            statService.incr("seckill:soldout");
        } else {
            result = "DUPLICATE";
            msg = "重复抢购被拦截（一人一件限购）";
            statService.incr("seckill:duplicate");
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("result", result);
        m.put("msg", msg);
        m.put("userId", userId);
        m.put("remaining", remaining(productId));
        m.put("pendingOrders", pendingOrders());
        return m;
    }

    /** 后台消费者：阻塞式 rightPop，把削峰队列里的订单逐个落 MySQL */
    @PostConstruct
    public void startConsumer() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    String payload = stringRedisTemplate.opsForList()
                            .rightPop(RedisKeys.seckillPendingQueue(), 1, TimeUnit.SECONDS);
                    if (payload != null) {
                        persist(payload);
                    }
                } catch (Exception e) {
                    statService.incr("seckill:persist_error");
                }
            }
        }, "seckill-order-consumer");
        t.setDaemon(true);
        t.start();
    }

    @PreDestroy
    public void stopConsumer() {
        running = false;
    }

    private void persist(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            statService.incr("seckill:persist_error");
            return;
        }
        long productId = Long.parseLong(parts[0]);
        String userId = parts[1];
        long ts = Long.parseLong(parts[2]);
        try {
            jdbcTemplate.update(
                    "INSERT INTO seckill_order (product_id, user_id, create_time) VALUES (?, ?, ?)",
                    productId, userId, ts);
            statService.incr("seckill:persisted");
            persistDelayTotalMs.addAndGet(System.currentTimeMillis() - ts);
            persistCount.incrementAndGet();
        } catch (DuplicateKeyException e) {
            // 数据库层兜底：同一 (商品,用户) 重复写入被唯一约束拦下
            statService.incr("seckill:db_dedup");
        }
    }

    public long remaining(Long productId) {
        String v = stringRedisTemplate.opsForValue().get(RedisKeys.seckillStock(productId));
        return v == null ? 0L : Long.parseLong(v);
    }

    public long pendingOrders() {
        Long size = stringRedisTemplate.opsForList().size(RedisKeys.seckillPendingQueue());
        return size == null ? 0L : size;
    }

    /** 可观测性：一次返回该模块全部核心指标 */
    public Map<String, Object> status(Long productId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("remainingStock", remaining(productId));
        m.put("success", statService.get("seckill:success"));
        m.put("soldoutBlocked", statService.get("seckill:soldout"));
        m.put("duplicateBlocked", statService.get("seckill:duplicate"));
        m.put("pendingOrders", pendingOrders());
        m.put("persisted", statService.get("seckill:persisted"));
        m.put("dbDedupBlocked", statService.get("seckill:db_dedup"));
        m.put("persistErrors", statService.get("seckill:persist_error"));
        long cnt = persistCount.get();
        m.put("avgPersistDelayMs", cnt == 0 ? 0 : persistDelayTotalMs.get() / cnt);
        Integer db = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM seckill_order WHERE product_id = ?", Integer.class, productId);
        m.put("dbOrders", db == null ? 0 : db);
        return m;
    }

    /** 已落库订单（验证「异步削峰最终一致」） */
    public List<Map<String, Object>> orders(Long productId) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM seckill_order WHERE product_id = ? ORDER BY id DESC LIMIT 50", productId);
    }
}
