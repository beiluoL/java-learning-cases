package com.example.redislab.modules.delayqueue;

import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 场景14：延迟队列（订单超时自动关闭）——补齐「异步 / 延迟任务」维度。
 *
 * <p>典型业务：秒杀/下单后 10 分钟未支付自动关单并回补库存。用 Redis ZSet 实现：
 * score = 到期时间戳，member = 订单号；后台扫描取出 score &lt;= now 的订单。
 *
 * <p>并发正确性：取出必须**原子**。若「先 ZRANGEBYSCORE 再逐个 ZREM」，多消费者实例
 * 会拿到同一批订单重复关单。本项目用一段 Lua 在 Redis 单线程内一次完成「查到期 + 全部移除」，
 * 保证水平扩展多个消费者也不会重复消费。
 *
 * <p>可靠性：关单失败自动重新入队（延迟 1s）最多重试 3 次，仍失败则进死信 List 并标记 DEAD，
 * 避免消息静默丢失。订单实时状态存 Redis Hash，MySQL 仅做归档与前端历史查询。</p>
 */
@Service
public class DelayQueueService {

    /** KEYS:1=delay ZSet；ARGV:1=nowMs 2=批量上限。返回本次取出的订单号列表 */
    private static final String POP_DUE_LUA = """
            local items = redis.call('ZRANGEBYSCORE', KEYS[1], 0, ARGV[1], 'LIMIT', 0, ARGV[2])
            for i = 1, #items do
              redis.call('ZREM', KEYS[1], items[i])
            end
            return items
            """;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final StatService statService;
    private final RedisScript<List> popDueScript;

    private final AtomicLong closeDelayTotalMs = new AtomicLong();
    private final AtomicLong closeCount = new AtomicLong();
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean running = true;

    public DelayQueueService(StringRedisTemplate stringRedisTemplate,
                             JdbcTemplate jdbcTemplate,
                             StatService statService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.statService = statService;
        this.popDueScript = new DefaultRedisScript<>(POP_DUE_LUA, List.class);
    }

    /** 创建待支付订单，delaySeconds 后自动关单 */
    public Map<String, Object> create(String userId, int delaySeconds) {
        int delay = Math.max(1, Math.min(delaySeconds, 3600));
        long now = System.currentTimeMillis();
        long expireAt = now + delay * 1000L;
        String orderId = "ORD" + now + "-" + seq.incrementAndGet();

        // 1) 进延迟调度队列
        stringRedisTemplate.opsForZSet().add(RedisKeys.delayQueue(), orderId, expireAt);
        // 2) 订单实时状态（Redis Hash）
        Map<String, String> h = new LinkedHashMap<>();
        h.put("id", orderId);
        h.put("userId", userId);
        h.put("state", "WAIT_PAY");
        h.put("createdAt", String.valueOf(now));
        h.put("expireAt", String.valueOf(expireAt));
        stringRedisTemplate.opsForHash().putAll(RedisKeys.delayOrder(orderId), h);
        // 3) MySQL 归档（前端历史订单查询）
        jdbcTemplate.update(
                "INSERT INTO delay_order (id, user_id, state, created_at, expire_at) VALUES (?, ?, ?, ?, ?)",
                orderId, userId, "WAIT_PAY", now, expireAt);
        statService.incr("delayqueue:created");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", orderId);
        m.put("state", "WAIT_PAY");
        m.put("expireAt", expireAt);
        m.put("delaySeconds", delay);
        m.put("note", "订单 " + delay + "s 后未支付将自动关闭；提前支付则不会关单");
        return m;
    }

    /** 支付：从延迟队列移除并标记 PAID，后台消费者会跳过（skip_paid） */
    public Map<String, Object> pay(String orderId) {
        String orderKey = RedisKeys.delayOrder(orderId);
        Map<Object, Object> h = stringRedisTemplate.opsForHash().entries(orderKey);
        if (h.isEmpty()) {
            return result(orderId, "NOT_FOUND", "订单不存在");
        }
        String state = String.valueOf(h.get("state"));
        if (!"WAIT_PAY".equals(state)) {
            return result(orderId, "REJECTED", "订单当前状态为 " + state + "，无法支付（可能已超时关闭）");
        }
        stringRedisTemplate.opsForZSet().remove(RedisKeys.delayQueue(), orderId);
        stringRedisTemplate.opsForHash().put(orderKey, "state", "PAID");
        stringRedisTemplate.opsForHash().put(orderKey, "paidAt", String.valueOf(System.currentTimeMillis()));
        jdbcTemplate.update("UPDATE delay_order SET state = ? WHERE id = ?", "PAID", orderId);
        statService.incr("delayqueue:paid");
        return result(orderId, "PAID", "支付成功，已从延迟队列移除，不会被自动关单");
    }

    /** 手动取消 */
    public Map<String, Object> cancel(String orderId) {
        stringRedisTemplate.opsForZSet().remove(RedisKeys.delayQueue(), orderId);
        stringRedisTemplate.opsForHash().put(RedisKeys.delayOrder(orderId), "state", "CANCELLED");
        jdbcTemplate.update("UPDATE delay_order SET state = ? WHERE id = ?", "CANCELLED", orderId);
        statService.incr("delayqueue:cancelled");
        return result(orderId, "CANCELLED", "订单已取消");
    }

    /** 后台消费者：每 500ms 扫描一次到期订单 */
    @PostConstruct
    public void startConsumer() {
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    List<?> due = stringRedisTemplate.execute(
                            popDueScript,
                            Collections.singletonList(RedisKeys.delayQueue()),
                            String.valueOf(System.currentTimeMillis()), String.valueOf(BATCH_SIZE));
                    if (due != null) {
                        for (Object o : due) {
                            closeIfUnpaid(String.valueOf(o));
                        }
                    }
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    statService.incr("delayqueue:scan_error");
                }
            }
        }, "delayqueue-consumer");
        t.setDaemon(true);
        t.start();
    }

    @PreDestroy
    public void stopConsumer() {
        running = false;
    }

    /** 关单：仅当状态仍是 WAIT_PAY 才执行，避免与支付并发时误关 */
    private void closeIfUnpaid(String orderId) {
        String orderKey = RedisKeys.delayOrder(orderId);
        try {
            Map<Object, Object> h = stringRedisTemplate.opsForHash().entries(orderKey);
            if (h.isEmpty()) {
                return;
            }
            String state = String.valueOf(h.get("state"));
            if (!"WAIT_PAY".equals(state)) {
                statService.incr("delayqueue:skip_paid");
                return;
            }
            long expireAt = Long.parseLong(String.valueOf(h.get("expireAt")));
            long now = System.currentTimeMillis();

            stringRedisTemplate.opsForHash().put(orderKey, "state", "CLOSED");
            stringRedisTemplate.opsForHash().put(orderKey, "closedAt", String.valueOf(now));
            jdbcTemplate.update("UPDATE delay_order SET state = ?, closed_at = ? WHERE id = ?",
                    "CLOSED", now, orderId);

            statService.incr("delayqueue:closed");
            closeDelayTotalMs.addAndGet(Math.max(0, now - expireAt));
            closeCount.incrementAndGet();
        } catch (Exception e) {
            // 失败重试：最多 3 次，仍失败进死信，避免消息静默丢失
            Long retries = stringRedisTemplate.opsForValue().increment(RedisKeys.delayRetry(orderId));
            stringRedisTemplate.expire(RedisKeys.delayRetry(orderId), 1, TimeUnit.HOURS);
            if (retries != null && retries <= MAX_RETRY) {
                stringRedisTemplate.opsForZSet().add(
                        RedisKeys.delayQueue(), orderId, System.currentTimeMillis() + 1000);
                statService.incr("delayqueue:retry");
            } else {
                stringRedisTemplate.opsForList().leftPush(RedisKeys.delayDeadLetter(), orderId);
                stringRedisTemplate.opsForHash().put(orderKey, "state", "DEAD");
                jdbcTemplate.update("UPDATE delay_order SET state = ? WHERE id = ?", "DEAD", orderId);
                statService.incr("delayqueue:dead");
            }
        }
    }

    /** 可观测性：队列堆积、消费延迟、死信等核心指标 */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        Long pending = stringRedisTemplate.opsForZSet().zCard(RedisKeys.delayQueue());
        Long dead = stringRedisTemplate.opsForList().size(RedisKeys.delayDeadLetter());
        m.put("pending", pending == null ? 0 : pending);
        m.put("created", statService.get("delayqueue:created"));
        m.put("paid", statService.get("delayqueue:paid"));
        m.put("closed", statService.get("delayqueue:closed"));
        m.put("cancelled", statService.get("delayqueue:cancelled"));
        m.put("skipPaid", statService.get("delayqueue:skip_paid"));
        m.put("retry", statService.get("delayqueue:retry"));
        m.put("deadLetter", dead == null ? 0 : dead);
        m.put("scanErrors", statService.get("delayqueue:scan_error"));
        long cnt = closeCount.get();
        m.put("avgCloseDelayMs", cnt == 0 ? 0 : closeDelayTotalMs.get() / cnt);
        return m;
    }

    /** 历史订单（MySQL 归档），前端订单状态可视化 */
    public List<Map<String, Object>> orders() {
        return jdbcTemplate.queryForList(
                "SELECT * FROM delay_order ORDER BY created_at DESC LIMIT 50");
    }

    /** 清空演示数据（含 Redis 队列与 DB 归档） */
    public void reset() {
        stringRedisTemplate.delete(RedisKeys.delayQueue());
        stringRedisTemplate.delete(RedisKeys.delayDeadLetter());
        jdbcTemplate.update("DELETE FROM delay_order");
        statService.resetByPrefix("delayqueue:");
    }

    private Map<String, Object> result(String orderId, String state, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", orderId);
        m.put("state", state);
        m.put("msg", msg);
        return m;
    }
}
