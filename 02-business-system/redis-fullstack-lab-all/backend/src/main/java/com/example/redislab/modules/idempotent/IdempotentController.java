package com.example.redislab.modules.idempotent;

import com.example.redislab.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 场景12：幂等处理接口。
 * POST /api/idempotent/token   签发一次性 token
 * POST /api/idempotent/submit  提交业务（幂等校验）
 * POST /api/idempotent/replay  并发重复请求模拟
 * GET  /api/idempotent/status  指标快照
 * GET  /api/idempotent/records 去重表记录
 * POST /api/idempotent/reset   清空演示数据
 */
@RestController
@RequestMapping("/api/idempotent")
public class IdempotentController {

    private final IdempotentService idempotentService;

    public IdempotentController(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    @PostMapping("/token")
    public R<Map<String, Object>> token() {
        return R.ok(idempotentService.createToken());
    }

    @PostMapping("/submit")
    public R<Map<String, Object>> submit(@RequestParam String token,
                                         @RequestParam String bizId,
                                         @RequestParam(defaultValue = "10") BigDecimal amount) {
        return R.ok(idempotentService.submit(token, bizId, amount));
    }

    @PostMapping("/replay")
    public R<Map<String, Object>> replay(@RequestParam String token,
                                         @RequestParam String bizId,
                                         @RequestParam(defaultValue = "10") BigDecimal amount,
                                         @RequestParam(defaultValue = "20") int times) {
        return R.ok(idempotentService.replay(token, bizId, amount, times));
    }

    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(idempotentService.status());
    }

    @GetMapping("/records")
    public R<List<Map<String, Object>>> records() {
        return R.ok(idempotentService.records());
    }

    @PostMapping("/reset")
    public R<Void> reset() {
        idempotentService.reset();
        return R.ok();
    }
}
