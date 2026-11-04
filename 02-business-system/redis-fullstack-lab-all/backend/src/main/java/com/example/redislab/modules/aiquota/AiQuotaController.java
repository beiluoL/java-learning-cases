package com.example.redislab.modules.aiquota;

import com.example.redislab.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 场景13：AI 额度管理接口（RPM / TPM 双滑动窗口）。
 * POST /api/aiquota/config   配置用户额度
 * POST /api/aiquota/consume  消费一次调用额度（返回放行/降级）
 * POST /api/aiquota/stress   突发流量模拟
 * GET  /api/aiquota/status   指标 + 水位
 * GET  /api/aiquota/usage    当前窗口用量
 * POST /api/aiquota/reset    清空该用户窗口
 */
@RestController
@RequestMapping("/api/aiquota")
public class AiQuotaController {

    private final AiQuotaService aiQuotaService;

    public AiQuotaController(AiQuotaService aiQuotaService) {
        this.aiQuotaService = aiQuotaService;
    }

    @PostMapping("/config")
    public R<Map<String, Object>> config(@RequestParam(defaultValue = "u1") String userId,
                                         @RequestParam(defaultValue = "10") int rpm,
                                         @RequestParam(defaultValue = "1000") int tpm) {
        return R.ok(aiQuotaService.config(userId, rpm, tpm));
    }

    @PostMapping("/consume")
    public R<Map<String, Object>> consume(@RequestParam(defaultValue = "u1") String userId,
                                          @RequestParam(defaultValue = "120") int tokens) {
        return R.ok(aiQuotaService.consume(userId, tokens));
    }

    @PostMapping("/stress")
    public R<Map<String, Object>> stress(@RequestParam(defaultValue = "u1") String userId,
                                         @RequestParam(defaultValue = "120") int tokens,
                                         @RequestParam(defaultValue = "20") int times) {
        return R.ok(aiQuotaService.stress(userId, tokens, times));
    }

    @GetMapping("/status")
    public R<Map<String, Object>> status(@RequestParam(defaultValue = "u1") String userId) {
        return R.ok(aiQuotaService.status(userId));
    }

    @GetMapping("/usage")
    public R<Map<String, Object>> usage(@RequestParam(defaultValue = "u1") String userId) {
        return R.ok(aiQuotaService.usage(userId));
    }

    @PostMapping("/reset")
    public R<Void> reset(@RequestParam(defaultValue = "u1") String userId) {
        aiQuotaService.reset(userId);
        return R.ok();
    }
}
