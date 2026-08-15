package com.enterprise.erpcrm.module.system.entity;

import com.enterprise.erpcrm.common.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 系统角色实体（sys_role）。
 * <p>dataScope 取值见 {@code DataScopeType}：1 全部 2 自定义 3 本部门 4 本部门及以下 5 仅本人。</p>
 *
 * @author erp-crm-system
 */
@Getter
@Setter
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private Integer dataScope;
    private Integer status;
    private String remark;
    private String createBy;
    private String updateBy;

    /** 非持久化：自定义数据权限部门 id 列表（来自 sys_role_dept） */
    @TableField(exist = false)
    private java.util.List<Long> deptIds;
    /** 非持久化：菜单 id 列表（角色授权时使用） */
    @TableField(exist = false)
    private java.util.List<Long> menuIds;
}
