package com.example.redislab.modules.avalanche;

import com.example.redislab.common.R;
import com.example.redislab.entity.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景8：缓存雪崩。
 * GET  /api/avalanche/product/{id}?protect=true   读取（true=带防护多级缓存）
 * POST /api/avalanche/preheat?protect=true        预热（true=随机 TTL，false=固定 TTL）
 * POST /api/avalanche/stress?times=100&protect=true  模拟瞬时洪峰
 * POST /api/avalanche/redis-down?down=true       模拟 Redis 宕机（触发熔断降级）
 */
@RestController
@RequestMapping("/api/avalanche")
public class AvalancheController {

    private final AvalancheService avalancheService;

    public AvalancheController(AvalancheService avalancheService) {
        this.avalancheService = avalancheService;
    }

    @GetMapping("/product/{id}")
    public R<Product> get(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean protect) {
        return R.ok(protect ? avalancheService.loadProtected(id) : avalancheService.loadUnprotected(id));
    }

    @PostMapping("/preheat")
    public R<Void> preheat(@RequestParam boolean protect) {
        avalancheService.preheat(protect);
        return R.ok();
    }

    @PostMapping("/stress")
    public R<Void> stress(@RequestParam int times, @RequestParam boolean protect) {
        // 在 Controller 循环调用，确保 @CircuitBreaker 走代理生效
        for (int i = 0; i < times; i++) {
            long id = 1 + (i % 3);
            if (protect) {
                avalancheService.loadProtected(id);
            } else {
                avalancheService.loadUnprotected(id);
            }
        }
        return R.ok();
    }

    @PostMapping("/redis-down")
    public R<Void> redisDown(@RequestParam boolean down) {
        avalancheService.setRedisDown(down);
        return R.ok();
    }
}
