package com.ecommerce.learn.module.pay.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.order.service.OrderService;
import com.ecommerce.learn.module.pay.dto.PayNotifyDTO;
import com.ecommerce.learn.module.pay.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付回调接口（公开，由支付网关异步调用）
 */
@Slf4j
@Tag(name = "支付回调")
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final OrderService orderService;

    @Operation(summary = "支付结果异步通知（模拟网关回调）")
    @PostMapping("/notify")
    public Result<Void> notify(@Valid @RequestBody PayNotifyDTO dto) {
        log.info("收到支付回调: {}", dto);
        if (!payService.verifyNotify(dto)) {
            return Result.failed("回调校验失败");
        }
        if (dto.isSuccess()) {
            orderService.markPaid(dto.getOrderNo(), dto.getTradeNo());
        }
        return Result.success();
    }
}
