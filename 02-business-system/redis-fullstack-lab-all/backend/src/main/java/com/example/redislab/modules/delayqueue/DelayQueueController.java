package com.example.redislab.modules.delayqueue;

import com.example.redislab.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 场景14：延迟队列（订单超时自动关闭）接口。
 * POST /api/delayqueue/create  创建待支付订单（delaySeconds 后自动关单）
 * POST /api/delayqueue/pay     提前支付
 * POST /api/delayqueue/cancel  取消订单
 * GET  /api/delayqueue/status  队列堆积/消费延迟/死信指标
 * GET  /api/delayqueue/orders  历史订单
 * POST /api/delayqueue/reset   清空演示数据
 */
@RestController
@RequestMapping("/api/delayqueue")
public class DelayQueueController {

    private final DelayQueueService delayQueueService;

    public DelayQueueController(DelayQueueService delayQueueService) {
        this.delayQueueService = delayQueueService;
    }

    @PostMapping("/create")
    public R<Map<String, Object>> create(@RequestParam(defaultValue = "u1") String userId,
                                         @RequestParam(defaultValue = "10") int delaySeconds) {
        return R.ok(delayQueueService.create(userId, delaySeconds));
    }

    @PostMapping("/pay")
    public R<Map<String, Object>> pay(@RequestParam String orderId) {
        return R.ok(delayQueueService.pay(orderId));
    }

    @PostMapping("/cancel")
    public R<Map<String, Object>> cancel(@RequestParam String orderId) {
        return R.ok(delayQueueService.cancel(orderId));
    }

    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(delayQueueService.status());
    }

    @GetMapping("/orders")
    public R<List<Map<String, Object>>> orders() {
        return R.ok(delayQueueService.orders());
    }

    @PostMapping("/reset")
    public R<Void> reset() {
        delayQueueService.reset();
        return R.ok();
    }
}
