package com.example.redislab.modules.seckill;

import com.example.redislab.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 场景11：秒杀库存接口。
 * POST /api/seckill/init    库存预热
 * POST /api/seckill/flash   单次抢购（Lua 原子扣减）
 * GET  /api/seckill/status  指标快照
 * GET  /api/seckill/orders  已异步落库的订单
 */
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/init")
    public R<Map<String, Object>> init(@RequestParam(defaultValue = "1") Long productId,
                                       @RequestParam(defaultValue = "100") int stock) {
        return R.ok(seckillService.init(productId, stock));
    }

    @PostMapping("/flash")
    public R<Map<String, Object>> flash(@RequestParam(defaultValue = "1") Long productId,
                                        @RequestParam String userId) {
        return R.ok(seckillService.flash(productId, userId));
    }

    @GetMapping("/status")
    public R<Map<String, Object>> status(@RequestParam(defaultValue = "1") Long productId) {
        return R.ok(seckillService.status(productId));
    }

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> orders(@RequestParam(defaultValue = "1") Long productId) {
        return R.ok(seckillService.orders(productId));
    }
}
