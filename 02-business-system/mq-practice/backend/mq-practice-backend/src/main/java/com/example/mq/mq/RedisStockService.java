package com.example.mq.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀库存的 Redis 预扣层。
 *
 * <h3>为什么用 Lua 而不是 GET + 判断 + DECRBY？</h3>
 * 因为 Redis 单条命令原子，但「多条命令」不原子。GET 到 1 件、另一个请求也 GET 到 1 件，
 * 两个都 DECRBY 就超卖了。Lua 脚本在 Redis 内单线程执行，天然原子，是官方推荐的 CAS 写法。
 *
 * <h3>Redis 里扣了，数据库还要扣吗？</h3>
 * 要。Redis 只承担「挡流量」的职责（把 90% 的无效请求拦在数据库之外），
 * <b>真正的库存事实仍然在 MySQL 的条件更新里</b>。这样即使 Redis 数据错乱也不会真的超卖，
 * 代价是极端情况下出现「Redis 扣成功但 DB 扣失败」，此时回滚 Redis 并置订单为 FAILED。
 */
@Slf4j
@Component
public class RedisStockService {

    private static final String KEY_PREFIX = "seckill:stock:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> seckillStockScript;

    public RedisStockService(StringRedisTemplate redisTemplate, DefaultRedisScript<Long> seckillStockScript) {
        this.redisTemplate = redisTemplate;
        this.seckillStockScript = seckillStockScript;
    }

    /** 预热/重置库存 */
    public void initStock(Long productId, int available) {
        redisTemplate.opsForValue().set(key(productId), String.valueOf(available));
    }

    /**
     * 原子预扣。
     *
     * @return {@link StockDeductResult#SUCCESS} 时 {@link DeductOutcome#getRemaining()} 为剩余库存
     */
    public DeductOutcome preDeduct(Long productId, int quantity) {
        Long result = redisTemplate.execute(seckillStockScript,
                List.of(key(productId)), String.valueOf(quantity));
        if (result == null) {
            return new DeductOutcome(StockDeductResult.NOT_INITIALIZED, -1);
        }
        if (result == -1L) {
            return new DeductOutcome(StockDeductResult.NOT_INITIALIZED, -1);
        }
        if (result == -2L) {
            return new DeductOutcome(StockDeductResult.SOLD_OUT, 0);
        }
        return new DeductOutcome(StockDeductResult.SUCCESS, result.intValue());
    }

    /** 回滚预扣（消息发送失败、DB 扣减失败时调用） */
    public void rollback(Long productId, int quantity) {
        try {
            redisTemplate.opsForValue().increment(key(productId), quantity);
        } catch (Exception e) {
            log.error("[STOCK] Redis 库存回滚失败 productId={} quantity={}", productId, quantity, e);
        }
    }

    /** 释放库存（订单取消时回补） */
    public void restore(Long productId, int quantity) {
        rollback(productId, quantity);
    }

    /** 查询 Redis 当前库存，null 表示未预热 */
    public Integer currentStock(Long productId) {
        try {
            String value = redisTemplate.opsForValue().get(key(productId));
            return value == null ? null : Integer.valueOf(value);
        } catch (Exception e) {
            log.warn("[STOCK] 读取 Redis 库存失败 productId={}：{}", productId, e.getMessage());
            return null;
        }
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }

    /** 预扣结果载体 */
    @lombok.Getter
    public static class DeductOutcome {
        private final StockDeductResult result;
        private final int remaining;

        public DeductOutcome(StockDeductResult result, int remaining) {
            this.result = result;
            this.remaining = remaining;
        }

        public boolean isSuccess() {
            return result == StockDeductResult.SUCCESS;
        }
    }

    /** 供外部读取脚本文件路径做自检（启动日志用） */
    public static String scriptLocation() {
        return new ClassPathResource("lua/seckill_stock.lua").getPath();
    }
}
