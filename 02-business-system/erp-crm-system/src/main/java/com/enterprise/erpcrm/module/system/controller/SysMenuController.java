package com.enterprise.erpcrm.module.system.controller;

import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.entity.SysMenu;
import com.enterprise.erpcrm.module.system.service.SysMenuService;
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
 * 系统菜单管理控制器。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    /** 菜单树（含按钮，用于管理界面） */
    @GetMapping("/tree")
    @OperLog(title = "菜单管理", businessType = BusinessType.QUERY)
    public Result<List<SysMenu>> tree() {
        return Result.success(menuService.buildTree());
    }

    @GetMapping("/{id}")
    public Result<SysMenu> get(@PathVariable Long id) {
        return Result.success(menuService.getById(id));
    }

    @PostMapping
    @OperLog(title = "菜单管理", businessType = BusinessType.INSERT)
    public Result<Void> add(@RequestBody SysMenu menu) {
        menuService.save(menu);
        return Result.success();
    }

    @PutMapping
    @OperLog(title = "菜单管理", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody SysMenu menu) {
        menuService.updateById(menu);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "菜单管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        menuService.removeById(id);
        return Result.success();
    }
}
