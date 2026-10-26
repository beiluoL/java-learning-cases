package com.finpay.business.web.gateway;

import com.finpay.common.result.Result;
import com.finpay.payment.core.model.dto.PayOrderRequest;
import com.finpay.payment.core.model.dto.PayOrderResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付网关示例 Controller。提供下单、退款、查询接口（需 JWT）。
 */
@RestController
@RequestMapping("/api/pay")
@CrossOrigin
public class PaymentGatewayController {

    private final PaymentOrderService orderService;

    public PaymentGatewayController(PaymentOrderService orderService) {
        this.orderService = orderService;
    }

    /** 统一下单 */
    @PostMapping("/create")
    public Result<PayOrderResult> create(@RequestBody PayOrderRequest req) {
        return Result.success(orderService.createPay(req));
    }

    /** 退款 */
    @PostMapping("/refund")
    public Result<?> refund(@RequestBody com.finpay.payment.core.model.dto.RefundRequest req) {
        return Result.success(orderService.refund(req));
    }

    /** 订单查询 */
    @GetMapping("/query")
    public Result<?> query(@RequestParam String channel,
                           @RequestParam String merchantNo,
                           @RequestParam String outTradeNo) {
        return Result.success(orderService.query(channel, merchantNo, outTradeNo));
    }

    /** 交易列表（管理后台交易查询页） */
    @GetMapping("/orders")
    public Result<?> orders(@RequestParam(required = false) String channel,
                            @RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(orderService.listOrders(channel, status, limit));
    }

    private Map<String, String> headersOf(HttpServletRequest req) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String n = names.nextElement();
                map.put(n, req.getHeader(n));
            }
        }
        return map;
    }
}
