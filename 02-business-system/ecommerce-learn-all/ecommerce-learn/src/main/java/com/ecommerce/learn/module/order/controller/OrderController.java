package com.ecommerce.learn.module.order.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.order.dto.*;
import com.ecommerce.learn.module.order.service.OrderService;
import com.ecommerce.learn.module.pay.dto.PayInfoVO;
import com.ecommerce.learn.security.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单接口（用户，需登录）
 */
@Tag(name = "订单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public Result<OrderCreateVO> create(@Valid @RequestBody CreateOrderDTO dto) {
        return Result.success(orderService.createOrder(UserContext.getUserId(), dto));
    }

    @Operation(summary = "我的订单")
    @GetMapping("/my")
    public Result<com.ecommerce.learn.common.result.PageResult<OrderVO>> myOrders(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.myOrders(UserContext.getUserId(), current, size));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.detail(UserContext.getUserId(), id));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(UserContext.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "发起支付")
    @PostMapping("/{id}/pay")
    public Result<PayInfoVO> pay(@PathVariable Long id,
                                 @RequestParam(defaultValue = "alipay") String channel) {
        return Result.success(orderService.pay(UserContext.getUserId(), id, channel));
    }

    @Operation(summary = "确认收货")
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        orderService.confirm(UserContext.getUserId(), id);
        return Result.success();
    }
}
