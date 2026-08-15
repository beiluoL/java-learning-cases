package com.enterprise.erpcrm.module.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.enterprise.erpcrm.common.base.BasePageQuery;
import com.enterprise.erpcrm.common.exception.BizException;
import com.enterprise.erpcrm.common.result.PageResult;
import com.enterprise.erpcrm.common.result.Result;
import com.enterprise.erpcrm.framework.security.PasswordUtils;
import com.enterprise.erpcrm.framework.security.SecurityHolder;
import com.enterprise.erpcrm.framework.security.annotation.OperLog;
import com.enterprise.erpcrm.framework.security.annotation.BusinessType;
import com.enterprise.erpcrm.module.system.domain.query.SysUserQuery;
import com.enterprise.erpcrm.module.system.entity.SysUser;
import com.enterprise.erpcrm.module.system.entity.SysUserRole;
import com.enterprise.erpcrm.module.system.mapper.SysUserRoleMapper;
import com.enterprise.erpcrm.module.system.service.SysUserService;
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
 * 系统用户管理控制器。
 *
 * @author erp-crm
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;
    private final SysUserRoleMapper userRoleMapper;

    /** 分页列表（受数据权限约束） */
    @GetMapping("/list")
    @OperLog(title = "用户管理", businessType = BusinessType.QUERY)
    public Result<PageResult<SysUser>> list(SysUserQuery query) {
        IPage<SysUser> page = userService.selectUserPage(query.toPage(), query);
        return Result.success(PageResult.of(page.getTotal(), page.getRecords()));
    }

    /** 详情 */
    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable Long id) {
        SysUser user = userService.getById(id);
        if (user != null) {
            List<Long> roleIds = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id))
                    .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            user.setRoleKeys(roleIds.stream().map(String::valueOf).collect(Collectors.toList()));
        }
        return Result.success(user);
    }

    /** 新增 */
    @PostMapping
    @OperLog(title = "用户管理", businessType = BusinessType.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> add(@RequestBody SysUser user) {
        userService.save(user);
        assignRoles(user.getId(), user.getRoleKeys());
        return Result.success();
    }

    /** 修改 */
    @PutMapping
    @OperLog(title = "用户管理", businessType = BusinessType.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> edit(@RequestBody SysUser user) {
        if (user.getId() != null && user.getId() == 1L) {
            throw new BizException("内置管理员不可修改");
        }
        userService.updateById(user);
        if (user.getRoleKeys() != null) {
            assignRoles(user.getId(), user.getRoleKeys());
        }
        return Result.success();
    }

    /** 删除（支持逗号分隔批量） */
    @DeleteMapping("/{ids}")
    @OperLog(title = "用户管理", businessType = BusinessType.DELETE)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> remove(@PathVariable List<Long> ids) {
        for (Long id : ids) {
            if (id == 1L) {
                throw new BizException("内置管理员不可删除");
            }
        }
        userService.removeByIds(ids);
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, ids));
        return Result.success();
    }

    /** 重置密码 */
    @PutMapping("/resetPwd")
    @OperLog(title = "用户管理", businessType = BusinessType.UPDATE)
    public Result<Void> resetPwd(@RequestParam Long userId, @RequestParam String password) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setPassword(PasswordUtils.encode(password));
        user.setUpdateBy(SecurityHolder.hasLogin() ? SecurityHolder.get().getUsername() : "system");
        userService.updateById(user);
        return Result.success();
    }

    private void assignRoles(Long userId, List<String> roleKeyStrs) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleKeyStrs == null || roleKeyStrs.isEmpty()) {
            return;
        }
        for (Long roleId : roleKeyStrs.stream().map(Long::valueOf).collect(Collectors.toList())) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }
}
