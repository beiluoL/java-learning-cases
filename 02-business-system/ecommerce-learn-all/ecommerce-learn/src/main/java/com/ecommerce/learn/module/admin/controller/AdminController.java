package com.ecommerce.learn.module.admin.controller;

import com.ecommerce.learn.common.result.Result;
import com.ecommerce.learn.module.admin.dto.AdminStatsVO;
import com.ecommerce.learn.module.admin.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台看板（需 ADMIN 角色）
 */
@Tag(name = "后台-看板")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;

    @Operation(summary = "首页统计数据")
    @GetMapping("/stats")
    public Result<AdminStatsVO> stats() {
        return Result.success(dashboardService.stats());
    }
}
