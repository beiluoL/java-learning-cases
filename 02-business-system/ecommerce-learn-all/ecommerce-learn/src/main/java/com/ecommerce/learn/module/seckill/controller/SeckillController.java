package com.ecommerce.learn.module.seckill.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.seckill.dto.SeckillActivityVO;
import com.ecommerce.learn.module.seckill.dto.SeckillResultVO;
import com.ecommerce.learn.module.seckill.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀公开接口
 */
@Tag(name = "秒杀")
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "秒杀活动列表")
    @GetMapping("/activity/list")
    public Result<List<SeckillActivityVO>> list() {
        return Result.success(seckillService.listActivities());
    }

    @Operation(summary = "活动详情")
    @GetMapping("/activity/{id}")
    public Result<SeckillActivityVO> detail(@PathVariable Long id) {
        return Result.success(seckillService.detail(id));
    }

    @Operation(summary = "参与秒杀")
    @PostMapping("/{activityId}/buy")
    public Result<SeckillResultVO> buy(@PathVariable Long activityId,
                                       @RequestParam Long skuId,
                                       @RequestParam(defaultValue = "1") int quantity) {
        Long userId = com.ecommerce.learn.security.UserContext.getUserId();
        return Result.success(seckillService.doSeckill(userId, activityId, skuId, quantity));
    }
}
