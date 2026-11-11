package com.example.ai.optimization;

import com.example.ai.common.ApiResponse;
import com.example.ai.common.BusinessException;
import com.example.ai.common.PageResult;
import com.example.ai.optimization.entity.CostOptimizationLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 成本优化中心：策略清单、在线试算、执行历史。 */
@RestController
@RequestMapping("/api/admin/cost-optimization")
public class CostOptimizationController {

    private final CostOptimizationService service;

    public CostOptimizationController(CostOptimizationService service) {
        this.service = service;
    }

    public record ExecuteRequest(String strategy, String text) {
    }

    @GetMapping("/strategies")
    public ApiResponse<List<CostOptimizationService.StrategyInfo>> strategies() {
        return ApiResponse.ok(service.strategies());
    }

    /** 只对纯函数策略开放试算；运行时策略（缓存/路由/去重）通过 /summary 查看真实效果。 */
    @PostMapping("/execute")
    public ApiResponse<CostOptimizationService.ExecuteResult> execute(@RequestBody ExecuteRequest body) {
        if (body == null || body.strategy() == null) {
            throw BusinessException.invalid("strategy 不能为空");
        }
        return ApiResponse.ok(service.execute(body.strategy().toUpperCase(), body.text()));
    }

    @GetMapping("/history")
    public ApiResponse<PageResult<CostOptimizationLog>> history(
            @RequestParam(required = false) String strategy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.history(strategy, page, size));
    }
}
