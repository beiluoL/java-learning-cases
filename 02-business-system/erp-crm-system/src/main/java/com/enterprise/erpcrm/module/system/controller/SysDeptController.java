package com.enterprise.erpcrm.module.system.controller;

import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.entity.SysDept;
import com.enterprise.erpcrm.module.system.service.SysDeptService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统部门管理控制器。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    /** 部门树 */
    @GetMapping("/tree")
    @OperLog(title = "部门管理", businessType = BusinessType.QUERY)
    public Result<List<SysDept>> tree() {
        return Result.success(deptService.buildTree());
    }

    @GetMapping("/{id}")
    public Result<SysDept> get(@PathVariable Long id) {
        return Result.success(deptService.getById(id));
    }

    @PostMapping
    @OperLog(title = "部门管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody SysDept dept) {
        deptService.save(dept);
        return Result.success();
    }

    @PutMapping
    @OperLog(title = "部门管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody SysDept dept) {
        deptService.updateById(dept);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "部门管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        deptService.removeById(id);
        return Result.success();
    }
}
