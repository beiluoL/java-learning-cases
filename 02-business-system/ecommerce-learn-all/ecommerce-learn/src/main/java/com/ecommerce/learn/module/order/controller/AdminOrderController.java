package com.ecommerce.learn.module.order.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.common.result.PageResult;
import com.ecommerce.learn.module.order.dto.AdminShipDTO;
import com.ecommerce.learn.module.order.dto.OrderVO;
import com.ecommerce.learn.module.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台订单管理（需 ADMIN 角色）
 */
@Tag(name = "后台-订单管理")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @Operation(summary = "订单列表")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> list(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.success(orderService.adminList(current, size));
    }

    @Operation(summary = "发货")
    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id, @Valid @RequestBody AdminShipDTO dto) {
        orderService.ship(id, dto.getLogisticsCompany(), dto.getLogisticsNo());
        return Result.success();
    }
}
