package com.finpay.business.web.gateway;

import com.finpay.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 渠道异步回调接收 Controller。路径含渠道与商户号，便于路由到对应配置。
 * 关键点：回调接口在 SecurityConfig 中已放行（免鉴权），但内部必须完成验签后才能入账。
 */
@RestController
@RequestMapping("/callback")
@CrossOrigin
public class CallbackController {

    private final PaymentOrderService orderService;

    public CallbackController(PaymentOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/{channel}/{merchantNo}")
    public String callback(@PathVariable String channel,
                           @PathVariable String merchantNo,
                           HttpServletRequest request) {
        try {
            String body = request.getReader().lines().reduce("", (a, b) -> a + b);
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String n = names.nextElement();
                headers.put(n, request.getHeader(n));
            }
            return orderService.handleCallback(channel, merchantNo, body, headers);
        } catch (Exception e) {
            // 回调处理失败必须返回非 success，渠道会按策略重试
            return "fail";
        }
    }
}
