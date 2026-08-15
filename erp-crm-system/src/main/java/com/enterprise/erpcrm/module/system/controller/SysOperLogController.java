package com.enterprise.erpcrm.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.erpcrm.common.base.BasePageQuery;
import com.enterprise.erpcrm.common.result.PageResult;
import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.entity.SysOperLog;
import com.enterprise.erpcrm.module.system.service.SysOperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/operlog")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

    @GetMapping("/list")
    @OperLog(title = "操作日志", businessType = BusinessType.QUERY)
    public Result<PageResult<SysOperLog>> list(BasePageQuery query, SysOperLog log) {
        var page = operLogService.page(query.toPage(),
                new LambdaQueryWrapper<SysOperLog>()
                        .like(log.getTitle() != null, SysOperLog::getTitle, log.getTitle())
                        .orderByDesc(SysOperLog::getOperTime));
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @DeleteMapping("/clean")
    @OperLog(title = "操作日志", businessType = BusinessType.CLEAN)
    public Result<Void> clean() {
        operLogService.remove(new LambdaQueryWrapper<>());
        return Result.success();
    }
}
