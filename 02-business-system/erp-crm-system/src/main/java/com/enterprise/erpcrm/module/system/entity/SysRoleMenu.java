package com.enterprise.erpcrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 角色-菜单关联实体（sys_role_menu）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_role_menu")
public class SysRoleMenu {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long roleId;
    private Long menuId;
}
