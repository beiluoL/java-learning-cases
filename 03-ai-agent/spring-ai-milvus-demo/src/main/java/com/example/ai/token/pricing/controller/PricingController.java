package com.example.ai.token.pricing.controller;

import com.example.ai.common.ApiResponse;
import com.example.ai.token.pricing.entity.ModelPricing;
import com.example.ai.token.pricing.service.ModelPricingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模型价格管理（受 /api/admin/** 权限保护）。价格全部来自数据库，代码不硬编码单价。 */
@RestController
@RequestMapping("/api/admin/ai/pricing")
public class PricingController {

    private final ModelPricingService service;

    public PricingController(ModelPricingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ModelPricing>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/history")
    public ApiResponse<List<ModelPricing>> history(@RequestParam(required = false) String modelName) {
        return ApiResponse.ok(service.history(modelName));
    }

    @PostMapping
    public ApiResponse<ModelPricing> create(@RequestBody ModelPricing pricing) {
        service.create(pricing);
        return ApiResponse.ok(pricing);
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelPricing> update(@PathVariable Long id, @RequestBody ModelPricing pricing) {
        service.update(id, pricing);
        return ApiResponse.ok(service.get(id));
    }
}
