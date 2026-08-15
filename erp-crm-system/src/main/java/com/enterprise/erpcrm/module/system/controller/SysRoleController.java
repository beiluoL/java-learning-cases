package com.enterprise.erpcrm.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.erpcrm.common.base.BasePageQuery;
import com.enterprise.erpcrm.common.result.PageResult;
import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.entity.SysMenu;
import com.enterprise.erpcrm.module.system.entity.SysRole;
import com.enterprise.erpcrm.module.system.mapper.SysRoleMenuMapper;
import com.enterprise.erpcrm.module.system.service.SysMenuService;
import com.enterprise.erpcrm.module.system.service.SysRoleService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统角色管理控制器。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final SysRoleMenuMapper roleMenuMapper;

    @GetMapping("/list")
    @OperLog(title = "角色管理", businessType = BusinessType.QUERY)
    public Result<PageResult<SysRole>> list(BasePageQuery query, SysRole role) {
        var page = roleService.page(query.toPage(),
                new LambdaQueryWrapper<SysRole>().like(role.getRoleName() != null, SysRole::getRoleName, role.getRoleName())
                        .orderByAsc(SysRole::getRoleSort));
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<SysRole> get(@PathVariable Long id) {
        SysRole role = roleService.getById(id);
        if (role != null) {
            List<Long> menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<com.enterprise.erpcrm.module.system.entity.SysRoleMenu>()
                                    .eq(com.enterprise.erpcrm.module.system.entity.SysRoleMenu::getRoleId, id))
                    .stream().map(com.enterprise.erpcrm.module.system.entity.SysRoleMenu::getMenuId).collect(Collectors.toList());
            role.setMenuIds(menuIds);
        }
        return Result.success(role);
    }

    @PostMapping
    @OperLog(title = "角色管理", businessType = BusinessType.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> add(@RequestBody SysRole role) {
        roleService.save(role);
        if (role.getMenuIds() != null) {
            roleService.authMenus(role.getId(), role.getMenuIds());
        }
        return Result.success();
    }

    @PutMapping
    @OperLog(title = "角色管理", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> edit(@RequestBody SysRole role) {
        roleService.updateById(role);
        if (role.getMenuIds() != null) {
            roleService.authMenus(role.getId(), role.getMenuIds());
        }
        if (role.getDeptIds() != null) {
            roleService.authDepts(role.getId(), role.getDeptIds());
        }
        return Result.success();
    }

    @DeleteMapping("/{ids}")
    @OperLog(title = "角色管理", businessType = BusinessType.DELETE)
    public Result<Void> remove(@PathVariable List<Long> ids) {
        roleService.removeByIds(ids);
        return Result.success();
    }

    /** 角色下拉选项 */
    @GetMapping("/options")
    public Result<List<SysRole>> options() {
        return Result.success(roleService.list());
    }

    /** 菜单树（角色授权勾选用，全量） */
    @GetMapping("/menuTree")
    public Result<List<SysMenu>> menuTree() {
        return Result.success(menuService.buildTree());
    }
}
