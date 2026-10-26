package com.enterprise.erpcrm.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.enterprise.erpcrm.module.system.entity.SysRole;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统角色 Mapper。
 *
 * @author erp-crm-system
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查询用户拥有的角色标识集合 */
    @Select("SELECT r.role_key FROM sys_role r "
            + "JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<String> selectRoleKeysByUserId(@Param("userId") Long userId);

    /** 查询用户拥有的角色 id 集合（计算数据权限范围用） */
    @Select("SELECT r.id, r.data_scope FROM sys_role r "
            + "JOIN sys_user_role ur ON r.id = ur.role_id "
            + "WHERE ur.user_id = #{userId} AND r.deleted = 0")
    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    /** 查询用户自定义数据权限可见的部门 id（角色 data_scope=2 且在 sys_role_dept 中授权） */
    @Select("SELECT DISTINCT rd.dept_id FROM sys_role_dept rd "
            + "JOIN sys_user_role ur ON rd.role_id = ur.role_id "
            + "JOIN sys_role r ON r.id = rd.role_id "
            + "WHERE ur.user_id = #{userId} AND r.data_scope = 2")
    List<Long> selectCustomDeptIds(@Param("userId") Long userId);
}
