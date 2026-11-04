package com.example.redislab.modules.breakdown;

import com.example.redislab.common.R;
import com.example.redislab.entity.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景7：缓存击穿（热点 key 重建）。
 * GET  /api/breakdown/mutex/{id}     互斥锁方案
 * GET  /api/breakdown/logic/{id}     逻辑过期方案（非阻塞）
 * POST /api/breakdown/expire/mutex/{id}  让热点 key 立即「过期」，触发并发重建
 * POST /api/breakdown/expire/logic/{id}  让逻辑过期时间失效
 */
@RestController
@RequestMapping("/api/breakdown")
public class BreakdownController {

    private final BreakdownService breakdownService;

    public BreakdownController(BreakdownService breakdownService) {
        this.breakdownService = breakdownService;
    }

    @GetMapping("/mutex/{id}")
    public R<Product> mutex(@PathVariable Long id) {
        return R.ok(breakdownService.getHotMutex(id));
    }

    @GetMapping("/logic/{id}")
    public R<Product> logic(@PathVariable Long id) {
        return R.ok(breakdownService.getHotLogical(id));
    }

    @PostMapping("/expire/mutex/{id}")
    public R<Void> expireMutex(@PathVariable Long id) {
        breakdownService.forceExpireMutex(id);
        return R.ok();
    }

    @PostMapping("/expire/logic/{id}")
    public R<Void> expireLogical(@PathVariable Long id) {
        breakdownService.forceExpireLogical(id);
        return R.ok();
    }
}
