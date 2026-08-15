package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.erpcrm.framework.security.datascope.DeptDataRepository;
import com.enterprise.erpcrm.module.system.entity.SysRoleDept;
import com.enterprise.erpcrm.module.system.entity.SysUserRole;
import com.enterprise.erpcrm.module.system.mapper.SysRoleDeptMapper;
import com.enterprise.erpcrm.module.system.mapper.SysUserRoleMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 数据权限部门数据源实现：为 {@code DataScopeAspect} 提供自定义部门与子部门查询。
 *
 * @author erp-crm-system
 */
@Repository
@RequiredArgsConstructor
public class DeptDataRepositoryImpl implements DeptDataRepository {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysDeptServiceImpl deptService;

    @Override
    public List<Long> selectCustomDeptIds(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleDeptMapper.selectList(
                        new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, roleIds))
                .stream().map(SysRoleDept::getDeptId).collect(Collectors.toList());
    }

    @Override
    public List<Long> selectChildDeptIds(Long deptId) {
        return deptService.selectChildDeptIds(deptId);
    }
}
