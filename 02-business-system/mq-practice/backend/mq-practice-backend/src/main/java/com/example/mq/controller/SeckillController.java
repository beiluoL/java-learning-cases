package com.example.mq.controller;

import com.example.mq.common.api.Result;
import com.example.mq.dto.ProductView;
import com.example.mq.dto.SeckillRequest;
import com.example.mq.dto.SeckillResponse;
import com.example.mq.dto.StressTestResult;
import com.example.mq.service.ProductService;
import com.example.mq.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 【场景一】秒杀下单接口。
 * <p>
 * 接口设计要点：POST /orders 只返回"排队凭证"，不返回订单详情。
 * 前端拿到 orderNo 后<b>轮询</b> GET /api/orders/{orderNo} 获取真实结果 ——
 * 这是异步化的标准交互契约（另一种是 WebSocket/SSE 推送，见 README 的扩展说明）。
 */
@Tag(name = "场景一：秒杀下单（削峰填谷）")
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final ProductService productService;

    @Operation(summary = "秒杀商品列表（含数据库库存与 Redis 预扣库存）")
    @GetMapping("/products")
    public Result<List<ProductView>> products() {
        return Result.ok(productService.list());
    }

    @Operation(summary = "提交秒杀订单（立即返回排队中）")
    @PostMapping("/orders")
    public Result<SeckillResponse> submit(@Valid @RequestBody SeckillRequest request) {
        return Result.ok(seckillService.submit(request));
    }

    @Operation(summary = "并发压测：N 个线程同时抢购，验证不超卖")
    @PostMapping("/stress")
    public Result<StressTestResult> stress(@RequestParam Long productId,
                                           @RequestParam(defaultValue = "1") Integer quantity,
                                           @RequestParam(defaultValue = "50") Integer concurrency) {
        return Result.ok(seckillService.stressTest(productId, quantity, concurrency));
    }

    @Operation(summary = "重置演示库存（DB + Redis）")
    @PostMapping("/stock/reset")
    public Result<Void> resetStock(@RequestParam Long productId) {
        seckillService.resetStock(productId);
        return Result.ok(null, "库存已重置");
    }
}
