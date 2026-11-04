package com.example.redislab.modules.lock;

import com.example.redislab.common.R;
import com.example.redislab.common.RedisKeys;
import com.example.redislab.common.StatService;
import com.example.redislab.core.lock.LockService;
import com.example.redislab.service.StockService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景9 + 10：分布式锁扣库存。
 *
 * <p>通过 impl 参数切换「手写 Redis 锁」与「Redisson 锁」两种实现（共用同一套扣库存业务逻辑），
 * 前端并发压测时都能做到「库存从 100 扣到 0，无超卖」，但健壮性不同：
 * 手写锁需要自己写看门狗续期/可重入；Redisson 内置这些，生产首选。</p>
 *
 * POST /api/lock/deduct?impl=redis|redisson   扣 1 件（并发安全）
 * POST /api/lock/reset?count=100              重置库存（演示前清零）
 */
@RestController
@RequestMapping("/api/lock")
public class LockController {

    private final StockService stockService;
    private final LockService redisLockService;
    private final LockService redissonLockService;
    private final StatService statService;

    public LockController(StockService stockService,
                         @Qualifier("redisLockService") LockService redisLockService,
                         @Qualifier("redissonLockService") LockService redissonLockService,
                         StatService statService) {
        this.stockService = stockService;
        this.redisLockService = redisLockService;
        this.redissonLockService = redissonLockService;
        this.statService = statService;
    }

    @PostMapping("/deduct")
    public R<Map<String, Object>> deduct(@RequestParam(defaultValue = "1") Long productId,
                                        @RequestParam(defaultValue = "redis") String impl) {
        LockService lock = "redisson".equalsIgnoreCase(impl) ? redissonLockService : redisLockService;
        String lockKey = RedisKeys.lockStock(productId);

        String token = lock.tryLock(lockKey, 3, 10);
        if (token == null) {
            throw new RuntimeException("获取分布式锁失败（并发竞争激烈）");
        }
        try {
            int before = stockService.getCurrentCount(productId);
            if (before <= 0) {
                statService.incr("lock:oversold_block");
                return R.ok(buildResult(productId, impl, before, before, "已售罄，拒绝扣减"));
            }
            boolean ok = stockService.decrement(productId);
            int after = stockService.getCurrentCount(productId);
            if (ok) {
                statService.incr("lock:deduct_ok");
            }
            return R.ok(buildResult(productId, impl, before, after, ok ? "扣减成功" : "扣减失败"));
        } finally {
            lock.unlock(lockKey, token);
        }
    }

    @PostMapping("/reset")
    public R<Void> reset(@RequestParam(defaultValue = "1") Long productId,
                        @RequestParam(defaultValue = "100") int count) {
        stockService.reset(productId, count);
        statService.reset();
        return R.ok();
    }

    private Map<String, Object> buildResult(Long productId, String impl, int before, int after, String msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("productId", productId);
        map.put("impl", impl);
        map.put("before", before);
        map.put("after", after);
        map.put("msg", msg);
        return map;
    }
}
