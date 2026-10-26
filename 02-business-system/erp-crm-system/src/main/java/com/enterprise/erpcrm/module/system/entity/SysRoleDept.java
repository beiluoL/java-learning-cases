package com.enterprise.erpcrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-部门关联实体（sys_role_dept），仅当角色 dataScope=2（自定义部门）时生效。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_role_dept")
public class SysRoleDept {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long roleId;
    private Long deptId;
}
