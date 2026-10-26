package com.enterprise.erpcrm.common.constant;

import lombok.Getter;

/**
 * 数据权限范围枚举（RBAC 数据权限五档）。
 * <p>对应 {@code sys_role.data_scope} 字段值：</p>
 * <pre>
 *   1 ALL          全部数据（超级管理员 / 仓库 / 人事等）
 *   2 CUSTOM       自定义部门（仅可见指定部门，配合 sys_role_dept）
 *   3 DEPT         本部门
 *   4 DEPT_AND_CHILD 本部门及以下（基于 dept.ancestors 计算）
 *   5 SELF         仅本人（仅可见自己创建 / 负责的记录）
 * </pre>
 *
 * @author erp-crm-system
 */
@Getter
public enum DataScopeType {

    ALL(1, "全部数据"),
    CUSTOM(2, "自定义部门"),
    DEPT(3, "本部门"),
    DEPT_AND_CHILD(4, "本部门及以下"),
    SELF(5, "仅本人");

    private final int code;
    private final String desc;

    DataScopeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static DataScopeType of(int code) {
        for (DataScopeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return DEPT;
    }
}
