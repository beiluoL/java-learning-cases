package com.enterprise.erpcrm.module.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.enterprise.erpcrm.module.system.entity.SysRole;
import java.util.List;

/**
 * 系统角色服务接口。
 *
 * @author erp-crm-system
 */
public interface SysRoleService extends IService<SysRole> {

    /**
     * 查询用户拥有的角色标识集合（roleKey）。
     *
     * @param userId 用户 id
     * @return roleKey 列表，如 [SUPER_ADMIN]
     */
    List<String> selectRoleKeysByUserId(Long userId);

    /**
     * 查询用户拥有的角色 id 集合。
     *
     * @param userId 用户 id
     * @return 角色 id 列表
     */
    List<Long> selectRoleIdsByUserId(Long userId);

    /**
     * 依据角色 id 集合，聚合这些角色可访问的菜单权限标识（perms）。
     *
     * @param roleIds 角色 id 列表
     * @return 去重后的权限标识集合（含目录/菜单/按钮 perms）
     */
    List<String> selectPermsByRoleIds(List<Long> roleIds);

    /**
     * 查询用户角色的数据权限范围最小值（最宽松一档：1 全部最宽松）。
     *
     * @param userId 用户 id
     * @return 数据权限类型（1-5），无角色返回 5（仅本人）
     */
    int selectDataScopeByUserId(Long userId);

    /**
     * 授权：替换角色关联的菜单集合（先删后插）。
     *
     * @param roleId  角色 id
     * @param menuIds 菜单 id 列表
     */
    void authMenus(Long roleId, List<Long> menuIds);

    /**
     * 授权：替换角色关联的自定义数据权限部门集合。
     *
     * @param roleId  角色 id
     * @param deptIds 部门 id 列表
     */
    void authDepts(Long roleId, List<Long> deptIds);
}
