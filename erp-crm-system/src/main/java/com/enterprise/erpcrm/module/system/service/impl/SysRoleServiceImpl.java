package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.common.constant.ErpConstants;
import com.enterprise.erpcrm.module.system.entity.SysMenu;
import com.enterprise.erpcrm.module.system.entity.SysRole;
import com.enterprise.erpcrm.module.system.entity.SysRoleDept;
import com.enterprise.erpcrm.module.system.entity.SysRoleMenu;
import com.enterprise.erpcrm.module.system.entity.SysUserRole;
import com.enterprise.erpcrm.module.system.mapper.SysRoleDeptMapper;
import com.enterprise.erpcrm.module.system.mapper.SysRoleMenuMapper;
import com.enterprise.erpcrm.module.system.mapper.SysUserRoleMapper;
import com.enterprise.erpcrm.module.system.service.SysRoleService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统角色服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<com.enterprise.erpcrm.module.system.mapper.SysRoleMapper, SysRole>
        implements SysRoleService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final com.enterprise.erpcrm.module.system.mapper.SysMenuMapper menuMapper;

    @Override
    public List<String> selectRoleKeysByUserId(Long userId) {
        List<Long> roleIds = selectRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return listByIds(roleIds).stream().map(SysRole::getRoleKey).collect(Collectors.toList());
    }

    @Override
    public List<Long> selectRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    public List<String> selectPermsByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<String> perms = menuMapper.selectBatchIds(menuIds).stream()
                .map(SysMenu::getPerms)
                .filter(Objects::nonNull)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toList());
        // 超级管理员持有全部权限通配符
        if (perms.contains(ErpConstants.SUPER_ADMIN_PERM)) {
            return List.of(ErpConstants.SUPER_ADMIN_PERM);
        }
        return perms.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public int selectDataScopeByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return 5;
        }
        return listByIds(roleIds).stream()
                .map(SysRole::getDataScope)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(5);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void authMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (Long mid : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(mid);
                roleMenuMapper.insert(rm);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void authDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
        if (deptIds != null) {
            for (Long did : deptIds) {
                SysRoleDept rd = new SysRoleDept();
                rd.setRoleId(roleId);
                rd.setDeptId(did);
                roleDeptMapper.insert(rd);
            }
        }
    }
}
