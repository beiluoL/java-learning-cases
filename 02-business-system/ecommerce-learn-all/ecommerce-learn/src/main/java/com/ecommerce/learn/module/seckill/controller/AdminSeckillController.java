package com.ecommerce.learn.module.seckill.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.seckill.dto.SeckillActivitySaveDTO;
import com.ecommerce.learn.module.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台秒杀管理（需 ADMIN 角色）
 */
@Tag(name = "后台-秒杀管理")
@RestController
@RequestMapping("/api/admin/seckill")
@RequiredArgsConstructor
public class AdminSeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "新增/更新秒杀活动")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody SeckillActivitySaveDTO dto) {
        return Result.success(seckillService.saveActivity(dto));
    }

    @Operation(summary = "预热库存到 Redis")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        seckillService.publishStock(id);
        return Result.success();
    }
}
