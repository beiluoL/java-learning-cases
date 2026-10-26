package com.enterprise.erpcrm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户-角色关联实体（sys_user_role）。
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_user_role")
public class SysUserRole {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long roleId;
}
