package com.example.mq.controller;

import com.example.mq.common.api.PageResult;
import com.example.mq.common.api.Result;
import com.example.mq.dto.OrderView;
import com.example.mq.dto.SeckillRequest;
import com.example.mq.dto.SeckillResponse;
import com.example.mq.service.OrderService;
import com.example.mq.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口：既是场景一的「排队结果查询」，也是场景二的「支付/取消」入口。
 */
@Tag(name = "场景一/二：订单查询与支付")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SeckillService seckillService;

    @Operation(summary = "订单分页查询")
    @GetMapping
    public Result<PageResult<OrderView>> page(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(orderService.page(status, keyword, userId, page, size));
    }

    @Operation(summary = "按订单号查询（前端轮询排队结果用）")
    @GetMapping("/{orderNo}")
    public Result<OrderView> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @Operation(summary = "支付订单")
    @PostMapping("/{orderNo}/pay")
    public Result<OrderView> pay(@PathVariable String orderNo) {
        return Result.ok(orderService.pay(orderNo), "支付成功");
    }

    @Operation(summary = "主动取消订单（释放库存）")
    @PostMapping("/{orderNo}/cancel")
    public Result<OrderView> cancel(@PathVariable String orderNo,
                                    @RequestParam(required = false) String reason) {
        return Result.ok(orderService.cancel(orderNo, reason), "订单已取消");
    }

    /**
     * 创建一笔「可用于观察超时取消」的演示订单。
     * <p>
     * delaySeconds 传 10 就能在页面上几十秒内看到：待支付 → （延迟消息到点）→ 已取消，
     * 而不用等 30 分钟。
     */
    @Operation(summary = "创建演示订单（可指定支付超时秒数）")
    @PostMapping("/demo")
    public Result<SeckillResponse> createDemo(@Valid @RequestBody SeckillRequest request) {
        return Result.ok(seckillService.submit(request), "演示订单已受理");
    }
}
