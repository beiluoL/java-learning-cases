package com.example.redislab.modules.penetration;

import com.example.redislab.common.R;
import com.example.redislab.entity.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 场景6：缓存穿透。
 * GET  /api/penetration/product/{id}  正常查询（合法 id 走缓存，非法 id 被布隆拦截）
 * POST /api/penetration/attack        模拟批量攻击（恶意刷不存在的 id）
 */
@RestController
@RequestMapping("/api/penetration")
public class PenetrationController {

    private final PenetrationService penetrationService;

    public PenetrationController(PenetrationService penetrationService) {
        this.penetrationService = penetrationService;
    }

    @GetMapping("/product/{id}")
    public R<Product> get(@PathVariable Long id) {
        return R.ok(penetrationService.getProduct(id));
    }

    @PostMapping("/attack")
    public R<Map<String, Object>> attack(@RequestParam(defaultValue = "20") int count) {
        return R.ok(penetrationService.attack(count));
    }
}
